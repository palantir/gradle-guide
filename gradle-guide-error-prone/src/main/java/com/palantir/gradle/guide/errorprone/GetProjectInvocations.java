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
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;
import java.util.List;

@AutoService(BugChecker.class)
@BugPattern(severity = SeverityLevel.ERROR, summary = GetProjectInvocations.SUMMARY)
public final class GetProjectInvocations extends GradleGuideBugChecker implements BugChecker.MethodTreeMatcher {
    private static final Matcher<ExpressionTree> TASK_GET_PROJECT_METHOD = MethodMatchers.instanceMethod()
            .onDescendantOf("org.gradle.api.Task")
            .named("getProject");

    @SuppressWarnings("MemoizeConstantVisitorStateLookups")
    private boolean isExecuteOverride(MethodTree tree, VisitorState state) {
        if (!tree.getName().contentEquals("execute")) return false;

        if (tree.getParameters().size() != 1) return false;
        Symbol paramSym = ASTHelpers.getSymbol(tree.getParameters().get(0));
        if (paramSym == null
                || !ASTHelpers.getType(tree.getParameters().get(0)).toString().equals("org.gradle.api.Task")) {
            return false;
        }

        ClassTree enclosingClassTree = ASTHelpers.findEnclosingNode(state.getPath(), ClassTree.class);
        if (enclosingClassTree == null) return false;
        Symbol.ClassSymbol classSym = ASTHelpers.getSymbol(enclosingClassTree);
        if (classSym == null) return false;

        Symbol.ClassSymbol actionSym = (Symbol.ClassSymbol) state.getSymbolFromString("org.gradle.api.Action");
        Symbol.ClassSymbol taskSym = (Symbol.ClassSymbol) state.getSymbolFromString("org.gradle.api.Task");

        List<Type> interfaces = ((ClassType) classSym.type).interfaces_field;
        boolean implementsActionOfTask = interfaces.stream()
                .anyMatch(ifaceType -> ifaceType.tsym.equals(actionSym)
                        && ifaceType.getTypeArguments().size() == 1
                        && ifaceType.getTypeArguments().get(0).tsym.equals(taskSym));
        if (!implementsActionOfTask) return false;

        return true;
    }

    private static final Matcher<MethodTree> TASK_EXECUTE_OVERRIDE =
            Matchers.methodWithClassAndName("org.gradle.api.Tasks", "execute");
    public static final String SUMMARY =
            """
        Don't call getProject() in task actions. Large, mutable Gradle model types like `Gradle`, `Settings`, or
        `Project` should not be passed into tasks as inputs. Instead, your tasks should take in the "smallest" type
        required for the task's functionality. For example, instead of taking in `Project` to later do
        `project.version`, you should declare the project version as a `Property<String>`.
        Doing so improves performance in two ways:
        1. It makes your tasks compatible with the configuration cache
        2. It prevents tasks from being rendered out-of-date by a mutation unrelated to the task, e.g. to `project.name`
        """;

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        boolean isTaskAction = tree.getModifiers().getAnnotations().stream()
                .anyMatch(annotation ->
                        state.getSourceForNode(annotation.getAnnotationType()).contains("TaskAction"));
        boolean isExecuteOverride = isExecuteOverride(tree, state);

        System.out.println("isTaskAction: " + isTaskAction);
        System.out.println("isExecuteOverride: " + isExecuteOverride);

        if (isTaskAction || isExecuteOverride) {
            reportAllViolations(tree.getBody(), state, TASK_GET_PROJECT_METHOD);
        }

        return Description.NO_MATCH;
    }

    private boolean reportAllViolations(Tree tree, VisitorState state, Matcher<ExpressionTree> violationMatcher) {
        return new TreeScanner<Boolean, Void>() {
            @Override
            public Boolean scan(Tree node, Void unused) {
                if (node == null) {
                    return false;
                }

                if (node instanceof ExpressionTree expr && violationMatcher.matches(expr, state)) {
                    System.out.println("reportAllViolations +1");
                    state.reportMatch(buildDescription(expr)
                            .setMessage("Don't call `getProject()` in task actions")
                            .build());
                    return true;
                }

                return node.accept(this, null);
            }

            @Override
            public Boolean reduce(Boolean r1, Boolean r2) {
                return (r1 != null && r1) || (r2 != null && r2);
            }
        }.scan(tree, null);
    }

    @Override
    public MoreInfoLink moreInfoLink() {
        return new MoreInfoHeadingLink(
                "diagnosing-build-performance.md", "Unrelated tasks not running in parallel within the same project");
    }
}
