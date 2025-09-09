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
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Type;
import java.util.List;
import java.util.Optional;

@AutoService(BugChecker.class)
@BugPattern(
        severity = SeverityLevel.ERROR,
        summary =
                """
        Instead of `task1.dependsOn(task2)`, wire up the outputs of task2 to the inputs of task1 using providers.
        """)
public class TaskDependsOn extends GradleGuideBugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> TASK_DEPENDS_ON = MethodMatchers.instanceMethod()
            .onDescendantOf("org.gradle.api.Task")
            .named("dependsOn");

    private static final Matcher<Tree> TASK_PROVIDER_SUBTYPE =
            Matchers.isSubtypeOf("org.gradle.api.tasks.TaskProvider");

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

        if (!isCustomTask(receiver, state)) {
            return Description.NO_MATCH;
        }

        return buildDescription(tree)
                .setMessage(
                        "Instead of `task1.dependsOn(task2)`, wire up the outputs of task2 to the inputs of task1 using providers")
                .build();
    }

    /**
     * @param arg Any expression you can pass to {@code Task::dependsOn} — <a href="https://docs.gradle.org/current/javadoc/org/gradle/api/Task.html#dependencies">list in docs</a>
     */
    private static boolean isCustomTask(ExpressionTree arg, VisitorState state) {
        Type taskType =
                TASK_PROVIDER_SUBTYPE.matches(arg, state) ? extractTaskType(arg, state) : ASTHelpers.getType(arg);
        Type genericTaskType = state.getTypeFromString("org.gradle.api.Task");
        return ASTHelpers.isSubtype(taskType, genericTaskType, state)
                && !ASTHelpers.isSameType(taskType, genericTaskType, state);
    }

    private static Type extractTaskType(ExpressionTree taskProvider, VisitorState state) {
        Type taskProviderType = ASTHelpers.getType(taskProvider);
        Type.ClassType classType = (Type.ClassType) taskProviderType;
        List<Type> typeArgs = classType.getTypeArguments();
        return typeArgs.get(0);
    }

    @Override
    public MoreInfoLink moreInfoLink() {
        return null;
    }
}
