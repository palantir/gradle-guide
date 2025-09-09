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

package com.palantir.gradle.guide.errorprone.taskexecution;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.palantir.gradle.guide.errorprone.GradleGuideBugChecker;
import com.sun.source.tree.MethodInvocationTree;
import java.util.List;
import java.util.stream.Stream;

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
        require the same Project object as input, they cannot run in parallel to prevent concurrent access.
        However, if their inputs are changed to Provider<String> name and Provider<String> version respectively,
        the tasks become independent and can execute in parallel.
        """)
public final class IllegalMethodCalledDuringTaskExecution extends GradleGuideBugChecker
        implements BugChecker.MethodInvocationTreeMatcher {
    private static final MethodCall getProject = new MethodCall("org.gradle.api.Task", "getProject");
    private static final MethodCall getLogger = new MethodCall("org.gradle.api.Project", "getLogger");

    private static final TaskExecutionViolation REPORT_GET_PROJECT =
            TaskExecutionViolation.warn(ChainedCall.of(getProject), "Don't call `getProject()` in task actions");
    private static final TaskExecutionViolation FIX_GET_PROJECT_GET_LOGGER = TaskExecutionViolation.fix(
            ChainedCall.of(getProject, getLogger),
            "Instead of `getProject().getLogger()`, just do `getLogger()`",
            GradleFix.of("getLogger()"));

    private static final List<TaskExecutionViolation> violationsInOrderOfSpecificity =
            Stream.of(FIX_GET_PROJECT_GET_LOGGER, REPORT_GET_PROJECT).sorted().toList();

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        for (TaskExecutionViolation violation : violationsInOrderOfSpecificity) {
            // Take the most specific fix/report, if any
            if (violation.fixOrReport(tree, state, this)) {
                return Description.NO_MATCH;
            }
        }

        return Description.NO_MATCH;
    }

    @Override
    public MoreInfoLink moreInfoLink() {
        return new MoreInfoHeadingLink("adopting-the-configuration-cache.md", "Solving Configuration Cache problems");
    }
}
