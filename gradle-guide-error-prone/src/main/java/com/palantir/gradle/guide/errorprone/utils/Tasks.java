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

package com.palantir.gradle.guide.errorprone.utils;

import com.google.errorprone.VisitorState;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;
import java.util.List;
import java.util.Optional;

public class Tasks {
    public static boolean isTask(ClassTree tree, VisitorState state) {
        return Matchers.isSubtypeOf("org.gradle.api.Task").matches(tree, state);
    }

    public static boolean isGetProject(ExpressionTree expr, VisitorState state) {
        return MethodMatchers.instanceMethod()
                .onDescendantOf("org.gradle.api.Task")
                .named("getProject")
                .matches(expr, state);
    }

    public static boolean isGetLogger(ExpressionTree expr, VisitorState state) {
        return MethodMatchers.instanceMethod()
                .onDescendantOf("org.gradle.api.Project")
                .named("getLogger")
                .matches(expr, state);
    }

    // Optional.empty() in projects without gradle on the classpath
    // In that case, we should `return Description.NO_MATCH`
    public static final Supplier<Optional<ClassSymbol>> ACTION_SYMBOL = VisitorState.memoize(
            s -> Optional.ofNullable((ClassSymbol) s.getSymbolFromString("org.gradle.api.Action")));
    public static final Supplier<Optional<ClassSymbol>> TASK_SYMBOL =
            VisitorState.memoize(s -> Optional.ofNullable((ClassSymbol) s.getSymbolFromString("org.gradle.api.Task")));

    @SuppressWarnings("ASTHelpersSuggestions")
    public static final Supplier<Optional<MethodSymbol>> EXECUTE_SYMBOL = state -> {
        Optional<ClassSymbol> actionMaybe = ACTION_SYMBOL.get(state);
        return actionMaybe.flatMap(action -> action.getEnclosedElements().stream()
                .filter(enclosed -> enclosed.getSimpleName().contentEquals("execute"))
                .filter(execute -> execute instanceof MethodSymbol)
                .map(execute -> (MethodSymbol) execute)
                .findAny());
    };

    public record TaskOrAction(ClassTree tree, Type type) {
        public enum Type {
            TASK,
            ACTION
        }
    }

    /**
     * Finds the first enclosing {@code Task} or {@code Action<Task>}.
     */
    public static Optional<TaskOrAction> findFirstEnclosingTaskOrAction(TreePath path, VisitorState state) {
        TreePath curr = path;
        while (curr.getParentPath() != null) {
            if (curr.getLeaf() instanceof ClassTree classTree) {
                if (TASK.matches(classTree, state)) {
                    return Optional.of(new TaskOrAction(classTree, TaskOrAction.Type.TASK));
                } else if (implementsActionOfTask(ASTHelpers.getSymbol(classTree), state)) {
                    return Optional.of(new TaskOrAction(classTree, TaskOrAction.Type.ACTION));
                }
            }
            curr = curr.getParentPath();
        }
        return Optional.empty();
    }

    public static boolean isTaskAction(MethodTree tree, VisitorState state) {
        return Matchers.hasAnnotation("org.gradle.api.tasks.TaskAction").matches(tree, state);
    }

    // Returns true if `tree` is an override of `public void execute(Task)` from `Action<Task>`
    public static boolean overridesExecute(MethodTree tree, VisitorState state) {
        MethodSymbol methodSymbol = ASTHelpers.getSymbol(tree);
        Optional<ClassSymbol> enclosingClass = Optional.ofNullable(ASTHelpers.enclosingClass(methodSymbol));
        return isExecute(methodSymbol, state)
                && enclosingClass.map(cls -> implementsActionOfTask(cls, state)).orElse(false);
    }

    private static boolean isExecute(MethodSymbol methodSymbol, VisitorState state) {
        Optional<MethodSymbol> executeMaybe = EXECUTE_SYMBOL.get(state);
        Optional<ClassSymbol> actionMaybe = ACTION_SYMBOL.get(state);
        if (executeMaybe.isEmpty() || actionMaybe.isEmpty()) {
            return false;
        }
        return methodSymbol.overrides(executeMaybe.get(), actionMaybe.get(), state.getTypes(), true);
    }

    private static boolean implementsActionOfTask(ClassSymbol classSym, VisitorState state) {
        Optional<ClassSymbol> actionMaybe = ACTION_SYMBOL.get(state);
        Optional<ClassSymbol> taskMaybe = TASK_SYMBOL.get(state);
        if (actionMaybe.isEmpty() || taskMaybe.isEmpty()) {
            return false;
        }

        List<Type> interfaces = ((ClassType) classSym.type).interfaces_field;
        return interfaces.stream()
                .anyMatch(ifaceType -> ifaceType.tsym.equals(actionMaybe.get())
                        && ifaceType.getTypeArguments().size() == 1
                        && ifaceType.getTypeArguments().get(0).tsym.equals(taskMaybe.get()));
    }

    private Tasks() {}
}
