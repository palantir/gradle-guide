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
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Type;
import java.util.Optional;

@AutoService(BugChecker.class)
@BugPattern(
        severity = SeverityLevel.ERROR,
        summary =
                """
        Instead of `task1.dependsOn(task2)`, wire up the outputs of task2 to the inputs of task1 using providers.
        Using `dependsOn` makes it easy to add unnecessary task dependencies — e.g. depending on `jar` when you just
        need `classes`. It is also a sign of bad task design. You should write tasks with explicit, fine-grained inputs
        and outputs. Having unnecessary dependencies adds unnecessary work to a build and hurts task parallelism.

        However, there are cases where `dependsOn` is unavoidable:
        1. Wiring up tasks to lifecycle tasks e.g. `build.dependsOn(myTask)`
        2. Tasks that do system-wide setup like installing npm. In these cases, we recommended that you specify the
            installation directory as an `@OutputDirectory` of the task anyways.

        This errorprone tries to detect legitimate uses of `dependsOn`. You can suppress false positives, but we
        encourage you to do so sparingly, and think about whether something can be improved about your task design.
        """)
public final class TaskDependsOn extends GradleGuideBugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> TASK_DEPENDS_ON = MethodMatchers.instanceMethod()
            .onDescendantOf("org.gradle.api.Task")
            .named("dependsOn");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!TASK_DEPENDS_ON.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        Optional<ExpressionTree> receiverMaybe = Optional.ofNullable(ASTHelpers.getReceiver(tree));
        if (receiverMaybe.isEmpty()) {
            return Description.NO_MATCH;
        }
        ExpressionTree receiver = receiverMaybe.get();
        Type taskType = ASTHelpers.getType(receiver);

        if (!isCustomTask(taskType, state)) {
            return Description.NO_MATCH;
        }

        return buildDescription(tree)
                .setMessage("Instead of `task1.dependsOn(task2)`, "
                        + "wire up the outputs of task2 to the inputs of task1 using providers")
                .build();
    }

    private static boolean isCustomTask(Type task, VisitorState state) {
        Type genericTaskType = state.getTypeFromString("org.gradle.api.DefaultTask");
        return ASTHelpers.isSubtype(task, genericTaskType, state)
                && !ASTHelpers.isSameType(task, genericTaskType, state);
    }

    @Override
    public MoreInfoLink moreInfoLink() {
        return new MoreInfoHeadingLink("diagnosing-build-performance.md", "Needlessly included tasks");
    }
}
