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

package com.palantir.gradle.guide.errorprone;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.util.ASTHelpers;
import com.palantir.gradle.guide.errorprone.utils.MethodCallGraph;
import com.palantir.logsafe.Preconditions;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.TypeSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.Types;
import java.util.List;

@AutoService(BugChecker.class)
@BugPattern(severity = SeverityLevel.ERROR, summary = GetProjectInvocations.SUMMARY)
public final class GetProjectInvocations extends GradleGuideBugChecker implements BugChecker.MethodTreeMatcher {
    private static final Supplier<ClassSymbol> ACTION_SYM =
            VisitorState.memoize(s -> (ClassSymbol) s.getSymbolFromString("org.gradle.api.Action"));
    private static final Supplier<ClassSymbol> TASK_SYM =
            VisitorState.memoize(s -> (ClassSymbol) s.getSymbolFromString("org.gradle.api.Task"));

    private static final Supplier<MethodSymbol> GET_PROJECT_SYM =
            (state) -> (MethodSymbol) TASK_SYM.get(state).getEnclosedElements().stream()
                    .filter(e -> e.getSimpleName().contentEquals("getProject"))
                    .findAny()
                    .get();

    private static final Matcher<ExpressionTree> TASK_GET_PROJECT_METHOD = MethodMatchers.instanceMethod()
            .onDescendantOf("org.gradle.api.Task")
            .named("getProject");

    public static final String VIOLATION_MESSAGE = "Don't call `getProject()` in task actions";

    public static Matcher<MethodTree> methodWithSubclassAndName(String baseClassName, String methodName) {
        return (methodTree, state) -> {
            MethodSymbol methodSymbol = ASTHelpers.getSymbol(methodTree);
            TypeSymbol enclosingClass = (TypeSymbol) methodSymbol.getEnclosingElement();

            return methodTree.getName().contentEquals(methodName)
                    && ASTHelpers.isSubtype(enclosingClass.type, state.getTypeFromString(baseClassName), state);
        };
    }

    private static final Matcher<MethodTree> TASK_GET_PROJECT_DECLARATION =
            methodWithSubclassAndName("org.gradle.api.DefaultTask", "getProject");

    public static final String SUMMARY =
            """
        Don't call `getProject()` in task actions. Large, mutable Gradle model types like `Gradle`, `Settings`, or
        `Project` should not be passed into tasks as inputs. Instead, your tasks should take in the "smallest" type
        required for the task's functionality. For example, instead of taking in `Project` to later do
        `project.version`, you should declare the project version as a `Property<String>`.
        Doing so improves performance in two ways:
        1. It makes your tasks compatible with the configuration cache
        2. It prevents tasks from being rendered out-of-date by a mutation unrelated to the task, e.g. to `project.name`
        """;

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        if (isTaskAction(tree, state) || overridesExecute(tree, state)) {
            //            JavacProcessingEnvironment javacEnv = JavacProcessingEnvironment.instance(state.context);
            //            Trees trees = Trees.instance(javacEnv);
            MethodCallGraph.scan(
                    tree,
                    methodSym -> isGetProjectOnTask(methodSym, state),
                    (methodSym, invocTrees) ->
                            invocTrees.forEach(invocTree -> state.reportMatch(buildDescription(invocTree)
                                    .setMessage(VIOLATION_MESSAGE)
                                    .build())));
        }

        return Description.NO_MATCH;
    }

    private boolean isGetProjectOnTask(MethodSymbol methodSymbol, VisitorState state) {
        return methodSymbol.overrides(GET_PROJECT_SYM.get(state), TASK_SYM.get(state), state.getTypes(), true);
    }

    private boolean isDefaultTaskOrSubclass(ClassSymbol classSymbol, VisitorState state) {
        Types types = state.getTypes();
        Type defaultTaskType = Preconditions.checkNotNull(state.getSymbolFromString("org.gradle.api.DefaultTask")).type;
        return types.isAssignable(classSymbol.type, defaultTaskType);
    }

    private static boolean isTaskAction(MethodTree tree, VisitorState state) {
        return tree.getModifiers().getAnnotations().stream().anyMatch(annotation -> {
            String source = state.getSourceForNode(annotation.getAnnotationType());
            return source.equals("TaskAction");
        });
    }

    // TODO: maybe use MethodSymbol::overrides
    // Returns true if `tree` is an override of `public void execute(Task)` from `Action<Task>`
    private static boolean overridesExecute(MethodTree tree, VisitorState state) {
        return matchesExecuteSignature(tree)
                && implementsActionOfTask(ASTHelpers.findEnclosingNode(state.getPath(), ClassTree.class), state);
    }

    private static boolean matchesExecuteSignature(MethodTree tree) {
        MethodSymbol sym = ASTHelpers.getSymbol(tree);
        List<Type> paramTypes = sym.getParameters().stream().map(v -> v.type).toList();

        return sym.isPublic()
                && !sym.isStatic()
                && sym.getReturnType().toString().equals("void")
                && sym.getSimpleName().contentEquals("execute")
                && paramTypes.size() == 1
                && paramTypes.get(0).toString().equals("org.gradle.api.Task");
    }

    private static boolean implementsActionOfTask(ClassTree tree, VisitorState state) {
        if (tree == null) {
            return false;
        }
        ClassSymbol classSym = ASTHelpers.getSymbol(tree);
        ClassSymbol actionSym = ACTION_SYM.get(state);
        ClassSymbol taskSym = TASK_SYM.get(state);

        List<Type> interfaces = ((ClassType) classSym.type).interfaces_field;
        return interfaces.stream()
                .anyMatch(ifaceType -> ifaceType.tsym.equals(actionSym)
                        && ifaceType.getTypeArguments().size() == 1
                        && ifaceType.getTypeArguments().get(0).tsym.equals(taskSym));
    }

    @Override
    public MoreInfoLink moreInfoLink() {
        return new MoreInfoHeadingLink(
                "diagnosing-build-performance.md", "Unrelated tasks not running in parallel within the same project");
    }
}
