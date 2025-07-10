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
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;
import java.util.List;
import java.util.Optional;

@AutoService(BugChecker.class)
@BugPattern(
        severity = SeverityLevel.ERROR,
        summary =
                """
        Don't call `getProject()` in task actions. Instead, your tasks should take in the "smallest" type
        required for the task's functionality. For example, instead getProject().version(), you should declare the
        project version as an `@Input public abstract Property<String>`.

        Doing so improves performance in two ways:
        1. It makes your tasks compatible with the configuration cache
        2. It increases task parallelism. When two tasks, such as printProjectName and printProjectVersion, both
        require the same Project object as input, they cannot run in parallel due to prevent concurrent access.
        However, if their inputs are changed to Provider<String> name and Provider<String> version respectively,
        the tasks become independent and can execute in parallel.
        """)
public final class IllegalMethodCalledDuringTaskExecution extends GradleGuideBugChecker
        implements BugChecker.MethodTreeMatcher {
    // Optional.empty() in projects without gradle on the classpath
    // In that case, we should `return Description.NO_MATCH`
    private static final Supplier<Optional<ClassSymbol>> ACTION_SYM = VisitorState.memoize(
            s -> Optional.ofNullable((ClassSymbol) s.getSymbolFromString("org.gradle.api.Action")));
    private static final Supplier<Optional<ClassSymbol>> TASK_SYM =
            VisitorState.memoize(s -> Optional.ofNullable((ClassSymbol) s.getSymbolFromString("org.gradle.api.Task")));

    @SuppressWarnings("ASTHelpersSuggestions")
    private static final Supplier<Optional<MethodSymbol>> EXECUTE_SYM = state -> {
        Optional<ClassSymbol> actionMaybe = ACTION_SYM.get(state);
        return actionMaybe.flatMap(action -> action.getEnclosedElements().stream()
                .filter(enclosed -> enclosed.getSimpleName().contentEquals("execute"))
                .filter(execute -> execute instanceof MethodSymbol)
                .map(execute -> (MethodSymbol) execute)
                .findAny());
    };

    private static final Matcher<ExpressionTree> TASK_GET_PROJECT_METHOD = MethodMatchers.instanceMethod()
            .onDescendantOf("org.gradle.api.Task")
            .named("getProject");

    public static final String VIOLATION_MESSAGE = "Don't call `getProject()` in task actions";

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        if (isTaskAction(tree, state) || overridesExecute(tree, state)) {
            reportAllViolations(state, TASK_GET_PROJECT_METHOD, VIOLATION_MESSAGE);
        }

        return Description.NO_MATCH;
    }

    private static boolean isTaskAction(MethodTree tree, VisitorState state) {
        return Matchers.hasAnnotation("org.gradle.api.tasks.TaskAction").matches(tree, state);
    }

    // Returns true if `tree` is an override of `public void execute(Task)` from `Action<Task>`
    private static boolean overridesExecute(MethodTree tree, VisitorState state) {
        MethodSymbol methodSymbol = ASTHelpers.getSymbol(tree);
        Optional<ClassSymbol> enclosingClass = Optional.ofNullable(ASTHelpers.enclosingClass(methodSymbol));
        return isExecute(methodSymbol, state)
                && enclosingClass.map(cls -> implementsActionOfTask(cls, state)).orElse(false);
    }

    private static boolean isExecute(MethodSymbol methodSymbol, VisitorState state) {
        Optional<MethodSymbol> executeMaybe = EXECUTE_SYM.get(state);
        Optional<ClassSymbol> actionMaybe = ACTION_SYM.get(state);
        if (executeMaybe.isEmpty() || actionMaybe.isEmpty()) {
            return false;
        }
        return methodSymbol.overrides(executeMaybe.get(), actionMaybe.get(), state.getTypes(), true);
    }

    private static boolean implementsActionOfTask(ClassSymbol classSym, VisitorState state) {
        Optional<ClassSymbol> actionMaybe = ACTION_SYM.get(state);
        Optional<ClassSymbol> taskMaybe = TASK_SYM.get(state);
        if (actionMaybe.isEmpty() || taskMaybe.isEmpty()) {
            return false;
        }

        List<Type> interfaces = ((ClassType) classSym.type).interfaces_field;
        return interfaces.stream()
                .anyMatch(ifaceType -> ifaceType.tsym.equals(actionMaybe.get())
                        && ifaceType.getTypeArguments().size() == 1
                        && ifaceType.getTypeArguments().get(0).tsym.equals(taskMaybe.get()));
    }

    private void reportAllViolations(
            VisitorState state, Matcher<ExpressionTree> violationMatcher, String violationMessage) {
        new SuppressibleTreePathScanner<Boolean, Void>(state) {
            @Override
            public Boolean visitMethodInvocation(MethodInvocationTree node, Void unused) {
                if (violationMatcher.matches(node, state)) {
                    state.reportMatch(
                            buildDescription(node).setMessage(violationMessage).build());
                }
                return super.visitMethodInvocation(node, null);
            }
        }.scan(state.getPath(), null);
    }

    @Override
    public MoreInfoLink moreInfoLink() {
        return new MoreInfoHeadingLink("adopting-the-configuration-cache.md", "Adopting the Configuration Cache");
    }
}
