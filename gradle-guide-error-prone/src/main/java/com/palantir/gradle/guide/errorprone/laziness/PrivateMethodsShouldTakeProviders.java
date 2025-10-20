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
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.palantir.gradle.guide.errorprone.GradleGuideBugChecker;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Modifier;

@AutoService(BugChecker.class)
@BugPattern(
        severity = SeverityLevel.ERROR,
        summary = "Private methods should take TaskProvider parameters instead of Task parameters when all "
                + "call sites pass TaskProvider.get()")
public final class PrivateMethodsShouldTakeProviders extends GradleGuideBugChecker
        implements BugChecker.CompilationUnitTreeMatcher {

    record MethodUsage(MethodInvocationTree callSite, VisitorState state, boolean isDirectCall) {}

    record MethodInfo(MethodTree declaration, List<Integer> taskParameters, List<MethodUsage> usages) {}

    private static final Matcher<ExpressionTree> TASK_PROVIDER_GET = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.tasks.TaskProvider")
            .named("get");

    @Override
    public Description matchCompilationUnit(CompilationUnitTree tree, VisitorState state) {
        if (!bestEffortModeEnabled(state)) {
            return Description.NO_MATCH;
        }

        // Find all private methods which take tasks as parameters
        Map<MethodSymbol, MethodInfo> candidates = new HashMap<>();

        new TreeScanner<Void, Void>() {
            @Override
            public Void visitMethod(MethodTree node, Void unused) {
                Symbol.MethodSymbol methodSymbol = ASTHelpers.getSymbol(node);
                if (methodSymbol == null) {
                    return super.visitMethod(node, unused);
                }

                MethodInfo methodInfo = new MethodInfo(node, new ArrayList<>(), new ArrayList<>());

                // Find Task parameters
                for (int i = 0; i < node.getParameters().size(); i++) {
                    VariableTree param = node.getParameters().get(i);
                    Type paramType = ASTHelpers.getType(param.getType());
                    Type taskType = state.getTypeFromString("org.gradle.api.Task");

                    if (paramType != null
                            && taskType != null
                            && state.getTypes().isSubtype(paramType, taskType)) {
                        methodInfo.taskParameters().add(i);
                    }
                }

                candidates.put(methodSymbol, methodInfo);
                return super.visitMethod(node, unused);
            }
        }.scan(tree, null);

        // Find all usages of those methods
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                Symbol.MethodSymbol methodSymbol = ASTHelpers.getSymbol(node);
                if (methodSymbol != null && candidates.containsKey(methodSymbol)) {
                    MethodInfo methodInfo = candidates.get(methodSymbol);
                    boolean isDirectCall = true; // Method references would be handled differently
                    methodInfo.usages.add(new MethodUsage(node, state, isDirectCall));
                }
                return super.visitMethodInvocation(node, unused);
            }
        }.scan(tree, null);

        // Filter for methods which should be refactored
        List<MethodInfo> refactorableMethods = new ArrayList<>();
        for (Map.Entry<Symbol.MethodSymbol, MethodInfo> entry : candidates.entrySet()) {
            MethodInfo methodInfo = entry.getValue();
            if (shouldRefactorMethod(methodInfo, state)) {
                refactorableMethods.add(methodInfo);
            }
        }

        if (!refactorableMethods.isEmpty()) {
            return buildRefactorDescription(refactorableMethods, state);
        }

        return Description.NO_MATCH;
    }

    private boolean shouldRefactorMethod(MethodInfo methodInfo, VisitorState state) {
        // Must be private
        if (!methodInfo.declaration.getModifiers().getFlags().contains(Modifier.PRIVATE)) {
            return false;
        }

        // Must have at least one Task parameter that can be refactored
        if (methodInfo.taskParameters.isEmpty()) {
            return false;
        }

        System.err.println("Found method with refactorable task parameter");

        // Must have at least one usage
        if (methodInfo.usages.isEmpty()) {
            System.err.println("Dropping due to no usages");
            return false;
        }

        // All usages must be direct calls (not method references)
        for (MethodUsage usage : methodInfo.usages) {
            if (!usage.isDirectCall) {
                System.err.println("Dropping because usage wasn't direct call");
                return false;
            }
        }

        // At least one task parameter should be refactorable
        for (int paramIndex : methodInfo.taskParameters) {
            if (shouldRefactorParameter(methodInfo, paramIndex, state)) {
                System.err.println("Refactoring parameter " + paramIndex);
                return true;
            }
        }

        System.err.println("DRopping because no refactorable parameters");
        return false;
    }

    private boolean shouldRefactorParameter(MethodInfo methodInfo, int paramIndex, VisitorState state) {
        // Check if this parameter is TaskProvider.get() in all usages
        for (MethodUsage usage : methodInfo.usages) {
            if (paramIndex >= usage.callSite.getArguments().size()) {
                return false;
            }
            ExpressionTree arg = usage.callSite.getArguments().get(paramIndex);
            if (!TASK_PROVIDER_GET.matches(arg, usage.state)) {
                return false;
            }
        }
        return true;
    }

    private Description buildRefactorDescription(List<MethodInfo> refactorableMethods, VisitorState state) {
        SuggestedFix.Builder fix = SuggestedFix.builder();

        for (MethodInfo methodInfo : refactorableMethods) {
            // Determine which parameters should be refactored
            List<Integer> refactorableParams = new ArrayList<>();
            for (int paramIndex : methodInfo.taskParameters) {
                if (shouldRefactorParameter(methodInfo, paramIndex, state)) {
                    refactorableParams.add(paramIndex);
                }
            }

            // Change method signature: Task params -> TaskProvider<Task> params (only for refactorable params)
            for (int paramIndex : refactorableParams) {
                VariableTree param = methodInfo.declaration.getParameters().get(paramIndex);

                fix.addImport("org.gradle.api.tasks.TaskProvider");
                fix.replace(param.getType(), "TaskProvider<" + state.getSourceForNode(param.getType()) + ">");
            }

            // Add .get() to parameter usages inside the method (only for refactorable params)
            for (int paramIndex : refactorableParams) {
                Symbol.VarSymbol paramSymbol = ASTHelpers.getSymbol(
                        methodInfo.declaration.getParameters().get(paramIndex));

                new TreeScanner<Void, Void>() {
                    @Override
                    public Void visitIdentifier(IdentifierTree node, Void unused) {
                        Symbol symbol = ASTHelpers.getSymbol(node);
                        if (paramSymbol.equals(symbol)) {
                            fix.postfixWith(node, ".get()");
                        }
                        return super.visitIdentifier(node, unused);
                    }
                }.scan(methodInfo.declaration.getBody(), null);
            }

            // Remove .get() from all call sites (only for refactorable params)
            for (MethodUsage usage : methodInfo.usages) {
                for (int paramIndex : refactorableParams) {
                    ExpressionTree arg = usage.callSite().getArguments().get(paramIndex);
                    if (arg instanceof MethodInvocationTree getCall
                            && getCall.getMethodSelect() instanceof MemberSelectTree memberSelect) {
                        fix.replace(arg, state.getSourceForNode(memberSelect.getExpression()));
                    }
                }
            }
        }

        // Use the first method as the primary method for the description
        return buildDescription(refactorableMethods.get(0).declaration)
                .addFix(fix.build())
                .build();
    }

    private static class FindEligibleMethods extends TreePathScanner<Void, Void> {
        final Map<MethodSymbol, MethodInfo> methods = new HashMap<>();
        final VisitorState state;

        FindEligibleMethods(VisitorState state) {
            this.state = state;
        }

        @Override
        public Void visitMethod(MethodTree node, Void unused) {
            Symbol.MethodSymbol methodSymbol = ASTHelpers.getSymbol(node);
            if (methodSymbol != null) {
                MethodInfo methodInfo = new MethodInfo(node, new ArrayList<>(), new ArrayList<>());

                // Find Task parameters
                for (int i = 0; i < node.getParameters().size(); i++) {
                    VariableTree param = node.getParameters().get(i);
                    Type paramType = ASTHelpers.getType(param.getType());
                    Type taskType = state.getTypeFromString("org.gradle.api.Task");

                    if (paramType != null
                            && taskType != null
                            && state.getTypes().isSubtype(paramType, taskType)) {
                        methodInfo.taskParameters().add(i);
                    }
                }

                methods.put(methodSymbol, methodInfo);
            }
            return super.visitMethod(node, unused);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
            Symbol.MethodSymbol methodSymbol = ASTHelpers.getSymbol(node);
            if (methodSymbol != null && methods.containsKey(methodSymbol)) {
                MethodInfo methodInfo = methods.get(methodSymbol);
                boolean isDirectCall = true; // Method references would be handled differently
                methodInfo.usages.add(new MethodUsage(node, state, isDirectCall));
            }
            return super.visitMethodInvocation(node, unused);
        }
    }

    @Override
    public MoreInfoLink moreInfoLink() {
        return new MoreInfoHeadingLink("avoiding-unnecessary-configuration.md", "Using `TaskProvider`s");
    }
}
