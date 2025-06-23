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
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;

@AutoService(BugChecker.class)
@BugPattern(severity = SeverityLevel.ERROR, summary = GetProjectInvocations.SUMMARY)
public final class GetProjectInvocations extends GradleGuideBugChecker implements BugChecker.MethodTreeMatcher {
    private static final Matcher<ExpressionTree> TASK_GET_PROJECT_METHOD = MethodMatchers.instanceMethod()
            .onDescendantOf("org.gradle.api.Task")
            .named("getProject");
    private static final Matcher<ExpressionTree> PLUGIN_GET_PROJECT_METHOD = MethodMatchers.instanceMethod()
            .onExactClass("org.gradle.api.Plugin")
            .named("getProject");
    public static final String SUMMARY = "Don't call getProject() in task actions";

    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        boolean isTaskAction = tree.getModifiers().getAnnotations().stream()
                .anyMatch(annotation ->
                        state.getSourceForNode(annotation.getAnnotationType()).contains("TaskAction"));
        if (!isTaskAction) {
            return Description.NO_MATCH;
        }

        if (checkForMethodCall(tree.getBody(), state, TASK_GET_PROJECT_METHOD)) {
            return buildDescription(tree)
                    .setMessage("Don't call getProject() in task actions")
                    .build();
        } else {
            return Description.NO_MATCH;
        }
    }

    private boolean checkForMethodCall(Tree tree, VisitorState state, Matcher<ExpressionTree> matcher) {
        return new TreeScanner<Boolean, Void>() {
            @Override
            public Boolean scan(Tree node, Void unused) {
                if (node == null) {
                    return false;
                }

                if (node instanceof ExpressionTree expr && matcher.matches(expr, state)) {
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
        return new MoreInfoPageLink(
                "https://docs.gradle.org/current/userguide/configuration_cache.html#config_cache:requirements");
    }
}
