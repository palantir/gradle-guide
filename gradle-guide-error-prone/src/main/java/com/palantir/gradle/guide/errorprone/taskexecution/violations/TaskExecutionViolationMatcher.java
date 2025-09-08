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

package com.palantir.gradle.guide.errorprone.taskexecution.violations;

import com.google.errorprone.VisitorState;
import com.sun.source.tree.MethodInvocationTree;
import java.util.List;
import java.util.Optional;

public class TaskExecutionViolationMatcher {
    private static final List<TaskExecutionViolation> violationsInOrderOfSpecificity =
            List.of(new GetProjectGetLogger(), new GetProject());

    /**
     * A method invocation might match multiple violations, e.g. {@code getProject().getLogger()} matches both
     * {@code GetProject} and {@code GetProjectGetLogger}. We want to get the most specific violation, as it provides
     * the best fix or warn we can give the user.
     */
    public static Optional<TaskExecutionViolation> matchMostSpecific(MethodInvocationTree tree, VisitorState state) {
        return violationsInOrderOfSpecificity.stream()
                .filter(violation -> violation.matches(tree, state))
                .findFirst();
    }

    private TaskExecutionViolationMatcher() {}
}
