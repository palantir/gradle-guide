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
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePathScanner;
import com.sun.tools.javac.code.Symbol;

@AutoService(BugChecker.class)
@BugPattern(severity = SeverityLevel.ERROR, summary = ".get bad TODO CHANGE THIS")
final class TaskActionTimeSafety extends BugChecker implements MethodInvocationTreeMatcher {
    private static final Matcher<ExpressionTree> MATCHER = Matchers.allOf(
            Matchers.instanceMethod()
                    .onDescendantOfAny("org.gradle.api.provider.Provider")
                    .namedAnyOf("get"),
            Matchers.not(Matchers.enclosingMethod(Matchers.hasAnnotation("org.gradle.api.tasks.TaskAction"))));

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!MATCHER.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        Symbol owner = ASTHelpers.getSymbol(state.findEnclosing(MethodTree.class)).owner;

        ImmutableGraph.Builder<Symbol> callGraphBuilder =
                GraphBuilder.directed().immutable();

        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                Symbol methodCalled = ASTHelpers.getSymbol(node.getMethodSelect());
                if (methodCalled.owner.equals(owner)) {
                    callGraphBuilder.putEdge(
                            ASTHelpers.getSymbol(
                                    state.withPath(getCurrentPath()).findEnclosing(MethodTree.class)),
                            methodCalled);
                }
                return super.visitMethodInvocation(node, unused);
            }
        }.scan(state.getPath().getCompilationUnit(), null);

        ImmutableGraph<Symbol> callGraph = callGraphBuilder.build();

        if (callGraph.edges().isEmpty()) {
            return buildDescription(tree).build();
        }

        // Check if all callers have the @TaskAction annotation
        //        boolean allCallersHaveTaskAction =
        //                callers.stream().allMatch(TaskActionTimeSafety::methodHasTaskActionAnnotation);
        //
        //        if (allCallersHaveTaskAction) {
        //            return Description.NO_MATCH;
        //        }

        return buildDescription(tree).build();
    }

    private static boolean methodHasTaskActionAnnotation(MethodTree caller) {
        return caller.getModifiers().getAnnotations().stream().anyMatch(annotation -> {
            String annotationType = ASTHelpers.getAnnotationMirror(annotation)
                    .getAnnotationType()
                    .toString();
            return "org.gradle.api.tasks.TaskAction".equals(annotationType);
        });
    }
}
