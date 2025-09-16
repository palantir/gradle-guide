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
import java.util.Set;

@AutoService(BugChecker.class)
@BugPattern(
        severity = SeverityLevel.ERROR,
        summary =
                """
        Instead of `task1.dependsOn(task2)`, wire up the outputs of task2 to the inputs of task1 using providers.
        Using `dependsOn` makes it easy to add unnecessary task dependencies — e.g. depending on `jar` when you just
        need `classes`. It also doesn't encourage you to write tasks with explicit, fine-grained inputs and outputs.
        Having unnecessary dependencies adds unnecessary work to a build and hurts task parallelism.
        """)
public final class TaskDependsOn extends GradleGuideBugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final Set<String> KNOWN_LIFECYCLE_TASKS = Set.of(
            // Core bundling tasks
            "org.gradle.api.tasks.bundling.Jar",
            "org.gradle.api.tasks.bundling.War",
            "org.gradle.api.tasks.bundling.Zip",
            "org.gradle.api.tasks.bundling.Tar",

            // Testing
            "org.gradle.api.tasks.testing.Test",

            // Compilation
            "org.gradle.api.tasks.compile.JavaCompile",

            // Documentation
            "org.gradle.api.tasks.javadoc.Javadoc",

            // Application
            "org.gradle.api.tasks.JavaExec",
            "org.gradle.api.tasks.application.CreateStartScripts",

            // Quality
            "org.gradle.api.plugins.quality.Checkstyle",
            "com.github.spotbugs.snom.SpotBugsTask",

            // Publishing
            "org.gradle.api.publish.maven.tasks.PublishToMavenRepository",
            "org.gradle.api.publish.maven.tasks.PublishToMavenLocal",
            "org.gradle.api.publish.maven.tasks.GenerateMavenPom",

            // Cleanup
            "org.gradle.api.tasks.Delete");

    private static final Matcher<ExpressionTree> TASK_DEPENDS_ON = MethodMatchers.instanceMethod()
            .onDescendantOf("org.gradle.api.Task")
            .named("dependsOn");

    /**
     * FELIX's SUGGESTION — MAKE @LifecycleTask an annotation
     */
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

        // It's alright to wire up lifecycle tasks with dependsOn
        if (isKnownLifecycleTask(taskType, state)) {
            return Description.NO_MATCH;
        }
        // We assume people don't write custom types for lifecycle tasks
        if (!isCustomTask(taskType, state)) {
            return Description.NO_MATCH;
        }

        return buildDescription(tree)
                .setMessage("Instead of `task1.dependsOn(task2)`, "
                        + "wire up the outputs of task2 to the inputs of task1 using providers")
                .build();
    }

    private static boolean isKnownLifecycleTask(Type task, VisitorState state) {
        return KNOWN_LIFECYCLE_TASKS.stream().anyMatch(lifecycle -> {
            Type knownLifecycletask = state.getTypeFromString(lifecycle);
            return ASTHelpers.isSameType(task, knownLifecycletask, state);
        });
    }

    private static boolean isCustomTask(Type task, VisitorState state) {
        Type genericTaskType = state.getTypeFromString("org.gradle.api.Task");
        return ASTHelpers.isSubtype(task, genericTaskType, state)
                && !ASTHelpers.isSameType(task, genericTaskType, state);
    }

    @Override
    public MoreInfoLink moreInfoLink() {
        return new MoreInfoHeadingLink("diagnosing-build-performance.md", "Needlessly included tasks");
    }
}
