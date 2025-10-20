/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.guide.errorprone.laziness;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.Replacements.CoalescePolicy;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.util.ASTHelpers;
import com.palantir.gradle.guide.errorprone.GradleGuideBugChecker;
import com.palantir.gradle.guide.errorprone.utils.ReplacementTracker.SuggestedFixBuilder;
import com.palantir.gradle.guide.errorprone.utils.TreeUtils;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import java.util.HashSet;
import java.util.Optional;
import java.util.WeakHashMap;

@AutoService(BugChecker.class)
@BugPattern(
        severity = SeverityLevel.ERROR,
        summary = "Do not call `Provider.get`. Instead, pass providers directly to methods that accept them, "
                + "or transform providers using `Provider.map` or `Provider.flatMap`, or combine providers using "
                + "`Provider.zip`. Calling `Provider.get` causes Gradle to lose track of implicit dependencies and "
                + "can lead to timing issues by reading values too early.")
public final class AvoidEagerlyEvaluatingProviders extends GradleGuideBugChecker
        implements BugChecker.MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> PROVIDER_GET = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.provider.Provider")
            .named("get");
    private static final Matcher<ExpressionTree> TASK_PROVIDER_GET = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.tasks.TaskProvider")
            .named("get");
    private static final Matcher<ExpressionTree> NAMED_DOMAIN_OBJECT_PROVIDER_GET = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.NamedDomainObjectProvider")
            .named("get");

    public static final Matcher<ExpressionTree> TASK_CONTAINER_NAMED = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.tasks.TaskContainer")
            .named("named");
    public static final Matcher<ExpressionTree> TASK_CONTAINER_REGISTER = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.tasks.TaskContainer")
            .named("register");

    private static final Supplier<Type> TASK_PROVIDER =
            VisitorState.memoize(state -> state.getTypeFromString("org.gradle.api.tasks.TaskProvider"));
    private static final Matcher<ExpressionTree> NEW_PROVIDER = Matchers.instanceMethod()
            .onDescendantOfAny("org.gradle.api.Project", "org.gradle.api.provider.ProviderFactory")
            .named("provider");

    private static final Matcher<ExpressionTree> DEPENDS_ON =
            Matchers.instanceMethod().onDescendantOf("org.gradle.api.Task").named("dependsOn");
    private static final Matcher<ExpressionTree> FINALIZED_BY =
            Matchers.instanceMethod().onDescendantOf("org.gradle.api.Task").named("finalizedBy");
    private static final Matcher<ExpressionTree> MUST_RUN_AFTER =
            Matchers.instanceMethod().onDescendantOf("org.gradle.api.Task").named("mustRunAfter");
    private static final Matcher<ExpressionTree> SHOULD_RUN_AFTER =
            Matchers.instanceMethod().onDescendantOf("org.gradle.api.Task").named("shouldRunAfter");
    private static final Matcher<ExpressionTree> TAKES_BOTH_TASK_AND_TASK_PROVIDERS =
            Matchers.anyOf(DEPENDS_ON, FINALIZED_BY, MUST_RUN_AFTER, SHOULD_RUN_AFTER);

    public static final Matcher<ExpressionTree> PROPERTY_SET = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.provider.Property")
            .named("set");
    private static final Matcher<ExpressionTree> REGULAR_FILE_PROPERTY_SET = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.file.RegularFileProperty")
            .named("set");
    private static final Matcher<ExpressionTree> REGULAR_FILE_PROPERTY_FILE_PROVIDER = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.file.RegularFileProperty")
            .named("fileProvider");

    // When we're adding a new lambda parameter, we have to check for clashes with other names.
    // We can do a scope-aware check against the existing code with TreeUtils.
    // To prevent clashes with newly added code, we do the naive thing of not reusing names for newly introduced
    // parameters in this compilation of this file.
    private static final WeakHashMap<CompilationUnitTree, HashSet<String>> namesUsed = new WeakHashMap<>();

    @Override
    public Description matchMethodInvocation(MethodInvocationTree providerGet, VisitorState state) {
        if (!PROVIDER_GET.matches(providerGet, state) || !bestEffortModeEnabled(state)) {
            return Description.NO_MATCH;
        }

        Description.Builder description = buildDescription(providerGet);
        SuggestedFixBuilder fix = new SuggestedFixBuilder();
        fix.setCoalescePolicy(CoalescePolicy.EXISTING_FIRST);

        // This cast always works, because the member select in <provider expression>.get() is always a MemberSelectTree
        // We cast here for convenience, so the methods consuming tree don't have to do it themselves
        MemberSelectTree memberSelect = (MemberSelectTree) providerGet.getMethodSelect();

        // Before all else, check if this is a Provider.get() not being passed, assigned, or chained to anything.
        // This usually is a result of AvoidEagerApis converting create(...) to register(...).get()
        // Technically this changes the behavior of the program, but we don't think anyone is eagerly evaluating their
        // Gradle providers on purpose.
        if (handleProviderGetNotBeingUsedForAnything(state, fix, providerGet, memberSelect)) {
            return description.addFix(fix.build()).build();
        }

        // Check if this is a TaskProvider.get() being assigned to a variable
        boolean isNamedDomainObjectContainerGet = NAMED_DOMAIN_OBJECT_PROVIDER_GET.matches(providerGet, state);
        boolean isTaskProviderGet = TASK_PROVIDER_GET.matches(providerGet, state);
        if ((isNamedDomainObjectContainerGet || isTaskProviderGet)
                && handleProviderGetBeingAssignedToVariable(state, fix, providerGet)) {
            return description.addFix(fix.build()).build();
        }

        // We can make more assumptions for TaskProviders, and hence make more intrusive refactors
        if (isTaskProviderGet && handleTaskProviderGet(state, fix, providerGet, memberSelect)) {
            return description.addFix(fix.build()).build();
        }

        // Check if this is a provider.get().foo() where foo also returns a provider
        // Convert it to provider.map(val -> val.foo()).get()
        if (handleChainedCallAlsoReturnsProviders(state, fix, providerGet)) {
            return description.addFix(fix.build()).build();
        }

        // Check if this is a foo(Provider.get()) where foo also accepts a provider
        // e.g. Property.set
        if (handleConsumingMethodAlsoTakesProvider(state, fix, providerGet)) {
            return description.addFix(fix.build()).build();
        }

        // Check if this is a providers.provider(() -> otherProvider.get())
        if (replaceNewProviderWithGetCalledInside(state, fix, providerGet, memberSelect)) {
            return description.addFix(fix.build()).build();
        }

        return buildDescription(providerGet).addFix(fix.build()).build();
    }

    private boolean handleTaskProviderGet(
            VisitorState state,
            SuggestedFixBuilder fix,
            MethodInvocationTree taskProviderGet,
            MemberSelectTree memberSelect) {

        // Check if this is being passed to a method which also takes the provider, e.g. dependsOn(TaskProvider.get())
        if (handleTaskProviderGetUsedAsArg(state, fix, taskProviderGet, memberSelect)) {
            return true;
        }

        // Check if this is task configuration e.g. TaskProvider.get().dependsOn(...), or TaskProvider.get().setFoo(...)
        if (handleTaskProviderGetUsedToConfigure(state, fix, taskProviderGet, memberSelect)) {
            return true;
        }

        return false;
    }

    private static boolean handleChainedCallAlsoReturnsProviders(
            VisitorState state, SuggestedFixBuilder fix, MethodInvocationTree tree) {

        // Check if this is provider.get().some().method().chain() where the chain returns a Provider
        Tree parent = state.getPath().getParentPath().getLeaf();
        Tree grandParent = state.getPath().getParentPath().getParentPath().getLeaf();
        if (!(parent instanceof MemberSelectTree && grandParent instanceof MethodInvocationTree chainedMethod)) {
            return false;
        }

        // Check if the chained method returns a Provider
        Type returnType = ASTHelpers.getReturnType(chainedMethod);
        if (!ASTHelpers.isSubtype(returnType, state.getTypeFromString("org.gradle.api.provider.Provider"), state)) {
            return false;
        }

        // Get the original provider expression (before .get())
        MemberSelectTree getCall = (MemberSelectTree) tree.getMethodSelect();
        String providerSource = state.getSourceForNode(getCall.getExpression());

        // Get the chained method call
        String chainedMethodSource = state.getSourceCode()
                .subSequence(state.getEndPosition(tree), state.getEndPosition(chainedMethod))
                .toString();

        HashSet<String> namesUsedInThisCompilation =
                namesUsed.computeIfAbsent(state.getPath().getCompilationUnit(), unused -> new HashSet<>());
        String paramName =
                TreeUtils.sensibleLambdaParameterName(state, getCall.getExpression(), namesUsedInThisCompilation);
        namesUsedInThisCompilation.add(paramName);
        String replacement =
                providerSource + ".map(" + paramName + " -> " + paramName + chainedMethodSource + ").get()";

        fix.replace(chainedMethod, replacement);
        return true;
    }

    private boolean handleConsumingMethodAlsoTakesProvider(
            VisitorState state, SuggestedFixBuilder fix, MethodInvocationTree providerGet) {
        // Check if the parent is a method invocation, and that providerGet is a terminal call, i.e. a direct argument
        // to parent.
        Tree parent = state.getPath().getParentPath().getLeaf();
        if (!(parent instanceof MethodInvocationTree consumingMethod)) {
            return false;
        }
        boolean isTerminalCall = consumingMethod.getArguments().contains(providerGet);
        if (!isTerminalCall) {
            return false;
        }

        // Get the original provider expression (before .get())
        MemberSelectTree getCall = (MemberSelectTree) providerGet.getMethodSelect();
        ExpressionTree provider = getCall.getExpression();
        String providerSource = state.getSourceForNode(provider);

        // FileSystemLocationProperty provides set(File) but not set(Provider<File>)
        // It uses fileProvider(Provider<File>) instead.
        if (REGULAR_FILE_PROPERTY_SET.matches(consumingMethod, state)) {
            if (isProviderOfFile(provider, state)) {
                String regularFileProperty =
                        state.getSourceForNode(((MemberSelectTree) consumingMethod.getMethodSelect()).getExpression());
                fix.replace(consumingMethod, regularFileProperty + ".fileProvider(" + providerSource + ")");
                return true;
            } else if (isRegularFileProperty(provider, state)) {
                fix.replace(providerGet, providerSource);
                return true;
            }
            return false;
        }

        if (REGULAR_FILE_PROPERTY_FILE_PROVIDER.matches(consumingMethod, state)) {
            if (isProviderOfFile(provider, state)) {
                fix.replace(providerGet, providerSource);
                return true;
            }
            return false;
        }

        // Check if the consuming method can accept Provider arguments
        if (!handlePropertySet(consumingMethod, state)) {
            return false;
        }

        // Replace provider.get() with just provider
        fix.replace(providerGet, providerSource);
        return true;
    }

    private boolean isRegularFileProperty(ExpressionTree provider, VisitorState state) {
        Type providerType = ASTHelpers.getType(provider);
        return ASTHelpers.isSameType(
                providerType, state.getTypeFromString("org.gradle.api.file.RegularFileProperty"), state);
    }

    private boolean isProviderOfFile(ExpressionTree provider, VisitorState state) {
        Type providerType = ASTHelpers.getType(provider);
        Optional<Type> typeParam = TreeUtils.getFirstTypeArgument(providerType);

        return typeParam.isPresent()
                && ASTHelpers.isSameType(typeParam.get(), state.getTypeFromString("java.io.File"), state);
    }

    private static boolean handlePropertySet(MethodInvocationTree propertySet, VisitorState state) {
        // Check for Property.set propertySet
        if (!PROPERTY_SET.matches(propertySet, state)) {
            return false;
        }

        // Get the Property's type parameter
        Type receiverType = ASTHelpers.getReceiverType(propertySet);
        Optional<Type> propertyTypeParam = TreeUtils.getFirstTypeArgument(receiverType);
        if (propertyTypeParam.isEmpty()) {
            return false;
        }

        // Get the Provider's type parameter from the argument
        ExpressionTree providerArg = propertySet.getArguments().get(0);
        if (!(providerArg instanceof MethodInvocationTree providerGetInvocation)) {
            return false;
        }
        if (!((providerGetInvocation).getMethodSelect() instanceof MemberSelectTree providerGet)) {
            return false;
        }

        Type providerType = ASTHelpers.getType(providerGet.getExpression());
        Optional<Type> providerTypeParam = TreeUtils.getFirstTypeArgument(providerType);
        if (providerTypeParam.isEmpty()) {
            return false;
        }

        // Check if types match
        return state.getTypes().isAssignable(providerTypeParam.get(), propertyTypeParam.get());
    }

    private static boolean handleTaskProviderGetUsedToConfigure(
            VisitorState state, SuggestedFixBuilder fix, MethodInvocationTree tree, MemberSelectTree memberSelectTree) {

        // If the chained call which contains tree is an ExpressionStatementTree, then the return value isn't used.
        // This means we can make more invasive refactors which change the type of the ExpressionStatementTree,
        // like turning taskProvider.get().dependsOn(task) into taskProvider.configure(t -> { t.dependsOn(task) })
        TreePath fullChainedCallPath = TreeUtils.getFullCallChain(state.getPath());
        TreePath callParentPath = fullChainedCallPath.getParentPath();
        if (!(callParentPath.getLeaf() instanceof ExpressionStatementTree expressionStatement)) {
            return false;
        }

        // The return values of ExpressionStatements are unused, we can now safely replace
        // myTask.get().a().b() with myTask.configure(myTaskValue -> myTaskValue.a().b())
        ExpressionTree providerExpression = memberSelectTree.getExpression();
        String providerSource = state.getSourceForNode(providerExpression);
        HashSet<String> namesUsedInThisCompilation =
                namesUsed.computeIfAbsent(state.getPath().getCompilationUnit(), unused -> new HashSet<>());
        String lambdaParamName =
                TreeUtils.sensibleLambdaParameterName(state, providerExpression, namesUsedInThisCompilation);
        namesUsedInThisCompilation.add(lambdaParamName);

        // Extract the chained method calls after .get()
        String chainedCalls = state.getSourceCode()
                .subSequence(state.getEndPosition(tree), state.getEndPosition(fullChainedCallPath.getLeaf()))
                .toString();

        // Create the configure block: provider.configure(task -> task.a().b())
        String replacement =
                providerSource + ".configure(" + lambdaParamName + " -> " + lambdaParamName + chainedCalls + ")";

        fix.replace(expressionStatement, replacement + ";");

        return true;
    }

    private static boolean handleProviderGetNotBeingUsedForAnything(
            VisitorState state,
            SuggestedFixBuilder fix,
            MethodInvocationTree taskProviderGet,
            MemberSelectTree memberSelect) {
        Tree parent = state.getPath().getParentPath().getLeaf();

        // This means get() is the last call in the chain, and the value isn't being used in a declaration.
        // e.g. "myProvider.get();", "project.getTasks().register('foo').get();"
        if (!(parent instanceof ExpressionStatementTree)) {
            return false;
        }

        // We'd love to remove the .get(), but a standalone variable is not a valid statement in java.
        if (memberSelect.getExpression() instanceof IdentifierTree) {
            return false;
        }

        String withoutGet = state.getSourceForNode(memberSelect.getExpression());
        fix.replace(taskProviderGet, withoutGet);

        return true;
    }

    private static boolean handleProviderGetBeingAssignedToVariable(
            VisitorState state, SuggestedFixBuilder fix, MethodInvocationTree providerGet) {

        if (!(providerGet.getMethodSelect() instanceof MemberSelectTree memberSelectTree)) {
            return false;
        }

        Tree parentLeaf = state.getPath().getParentPath().getLeaf();
        if (!(parentLeaf instanceof VariableTree varTree)) {
            return false;
        }

        Optional<TreePath> enclosingBlock = TreeUtils.findEnclosingBlock(state.getPath());
        if (enclosingBlock.isEmpty()) {
            return false;
        }

        // Change variable from task to TaskProvider or NamedDomainObjectProvider
        replaceVariableDeclarationTypeWithProvider(state, fix, varTree);

        // Remove the .get() call at declaration
        // By specifying the start and end positions of this fix manually, we can make the fix range more granular
        // compared to just doing the fix on the whole invocation tree of TaskProvider.get(), which would potentially
        // overlap with other fixes done in e.g. the lambda block of tasks.register("mytask", task -> {...}).get()
        int endPosofGetCall = state.getEndPosition(providerGet);
        int startPosOfGetCall = endPosofGetCall - 6;
        fix.replace(startPosOfGetCall, endPosofGetCall, "");

        // Find all usages of this variable in the enclosing block and add .get()
        Symbol varSymbol = ASTHelpers.getSymbol(varTree);
        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitIdentifier(IdentifierTree identifier, Void unused) {
                if (varSymbol.equals(ASTHelpers.getSymbol(identifier))) {
                    fix.postfixWith(identifier, ".get()");
                }
                return super.visitIdentifier(identifier, unused);
            }
        }.scan(enclosingBlock.get(), null);

        return true;
    }

    private static boolean handleTaskProviderGetUsedAsArg(
            VisitorState state, SuggestedFixBuilder fix, MethodInvocationTree tree, MemberSelectTree memberSelectTree) {

        // Check if the parent is a method invocation
        if (!(state.getPath().getParentPath().getLeaf() instanceof MethodInvocationTree parentMethodInvocation)) {
            return false;
        }

        // Check if the parent method is dependsOn
        if (!TAKES_BOTH_TASK_AND_TASK_PROVIDERS.matches(parentMethodInvocation, state)) {
            return false;
        }

        // Check if this get() call is an argument to dependsOn
        for (ExpressionTree arg : parentMethodInvocation.getArguments()) {
            if (arg.equals(tree)) {
                // Replace the provider.get() with just provider
                fix.replace(tree, state.getSourceForNode(memberSelectTree.getExpression()));
                return true;
            }
        }

        return false;
    }

    private static boolean replaceNewProviderWithGetCalledInside(
            VisitorState state, SuggestedFixBuilder fix, MethodInvocationTree tree, MemberSelectTree memberSelect) {

        Optional<TreePath> enclosingLambda = TreeUtils.pathToRoot(state.getPath())
                .filter(path -> path.getParentPath().getLeaf() instanceof LambdaExpressionTree
                        && path.getParentPath().getParentPath().getLeaf() instanceof MethodInvocationTree)
                .findFirst();
        if (enclosingLambda.isEmpty()) {
            return false;
        }

        TreePath lambdaBodyPath = enclosingLambda.get();
        MethodInvocationTree providerFactoryMethod = (MethodInvocationTree)
                lambdaBodyPath.getParentPath().getParentPath().getLeaf();
        if (!NEW_PROVIDER.matches(providerFactoryMethod, state)) {
            return false;
        }

        ExpressionTree provider = memberSelect.getExpression();
        String originalMethodExpression = state.getSourceForNode(provider);

        boolean lambdaBodyJustReturnsProviderValue = tree == lambdaBodyPath.getLeaf();
        if (lambdaBodyJustReturnsProviderValue) {
            fix.replace(providerFactoryMethod, originalMethodExpression);
            return true;
        }

        CharSequence sourceCode = state.getSourceCode();
        HashSet<String> namesUsedInThisCompilation =
                namesUsed.computeIfAbsent(state.getPath().getCompilationUnit(), unused -> new HashSet<>());
        String lambdaParameter = TreeUtils.sensibleLambdaParameterName(state, provider, namesUsedInThisCompilation);
        namesUsedInThisCompilation.add(lambdaParameter);

        String lambdaBodyChanged = sourceCode.subSequence(
                        TreeUtils.startPosition(lambdaBodyPath.getLeaf()), TreeUtils.startPosition(memberSelect))
                + lambdaParameter
                + sourceCode.subSequence(state.getEndPosition(tree), state.getEndPosition(lambdaBodyPath.getLeaf()));

        fix.replace(
                providerFactoryMethod,
                originalMethodExpression + ".map(" + lambdaParameter + " -> " + lambdaBodyChanged + ")");
        return true;
    }

    public static void replaceVariableDeclarationTypeWithProvider(
            VisitorState state, SuggestedFixBuilder fix, VariableTree variableTree) {

        @SuppressWarnings("for-rollout:MemoizeConstantVisitorStateLookups")
        boolean isTask = state.getTypes()
                .isSubtype(ASTHelpers.getType(variableTree.getType()), state.getTypeFromString("org.gradle.api.Task"));

        String type = state.getSourceForNode(variableTree.getType());
        if (isTask) {
            fix.addImport("org.gradle.api.tasks.TaskProvider");
            fix.replace(variableTree.getType(), String.format("TaskProvider<%s>", type));
        } else {
            fix.addImport("org.gradle.api.NamedDomainObjectProvider");
            fix.replace(variableTree.getType(), String.format("NamedDomainObjectProvider<%s>", type));
        }
    }

    @Override
    public MoreInfoLink moreInfoLink() {
        return new MoreInfoHeadingLink("avoiding-unnecessary-configuration.md", "Using `TaskProvider`s");
    }
}
