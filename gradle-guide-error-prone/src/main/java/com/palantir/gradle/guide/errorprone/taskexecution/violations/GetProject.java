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
import com.google.errorprone.bugpatterns.BugChecker;
import com.palantir.gradle.guide.errorprone.utils.Tasks;
import com.sun.source.tree.MethodInvocationTree;

public final class GetProject implements TaskExecutionViolation {

    /** Matches {@code task.getProject()}. */
    @Override
    public boolean matches(MethodInvocationTree illegalCall, VisitorState state) {
        return Tasks.TASK_GET_PROJECT.matches(illegalCall, state);
    }

    @Override
    public void fixOrReport(MethodInvocationTree illegalCall, VisitorState state, BugChecker bugchecker) {
        state.reportMatch(bugchecker
                .buildDescription(illegalCall)
                .setMessage("Don't call `getProject()` in task actions")
                .build());
    }
}
