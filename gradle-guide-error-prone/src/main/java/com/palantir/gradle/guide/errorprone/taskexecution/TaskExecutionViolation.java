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

import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.palantir.gradle.guide.errorprone.taskexecution.GradleFix.GradleFixContext;
import com.palantir.gradle.guide.errorprone.utils.Tasks;
import com.palantir.gradle.guide.errorprone.utils.Tasks.TaskOrAction;
import com.sun.source.tree.MethodInvocationTree;
import java.util.Optional;

/**
 * Represents strategies to report or auto-fix illegal methods and methods chained to it.
 */
public record TaskExecutionViolation(ChainedCall violation, String message, Optional<GradleFix> fix)
        implements Comparable<TaskExecutionViolation> {

    public static TaskExecutionViolation fix(ChainedCall violation, String message, GradleFix fix) {
        return new TaskExecutionViolation(violation, message, Optional.of(fix));
    }

    public static TaskExecutionViolation report(ChainedCall violation, String message) {
        return new TaskExecutionViolation(violation, message, Optional.empty());
    }

    @Override
    public int compareTo(TaskExecutionViolation other) {
        // More specific violations come before less specific violations
        return this.violation.chainLength() - other.violation.chainLength();
    }

    public boolean matches(MethodInvocationTree call, VisitorState state) {
        return violation.matches(call, state);
    }

    /**
     * Returns {@code true} if a fix or report was done for {@code illegalCall}.
     * Note that this is called on every method invocation, so performance matters.
     */
    public void fixOrReport(MethodInvocationTree illegalCall, VisitorState state, BugChecker bugChecker) {
        Optional<TaskOrAction> taskOrActionMaybe = Tasks.findFirstEnclosingTaskOrAction(state.getPath(), state);
        if (taskOrActionMaybe.isEmpty()) {
            return; // We can't do anything here
        }

        TaskOrAction taskOrAction = taskOrActionMaybe.get();
        Description.Builder descriptionBuilder =
                bugChecker.buildDescription(illegalCall).setMessage(message);

        GradleFixContext context = new GradleFixContext(illegalCall, violation.chainLength(), taskOrAction);
        switch (taskOrAction.type()) {
            case ACTION -> {
                // We can't inject stuff into `Action<Task>`s, as `Action`s can't be made abstract
                boolean canFix = fix.map(gradleFix -> !gradleFix.requiresServiceInjection())
                        .orElse(false);
                if (canFix) {
                    descriptionBuilder.addFix(fix.get().render(context, state));
                }
            }
            case TASK -> fix.ifPresent(gradleFix -> descriptionBuilder.addFix(gradleFix.render(context, state)));
        }

        state.reportMatch(descriptionBuilder.build());
    }
}
