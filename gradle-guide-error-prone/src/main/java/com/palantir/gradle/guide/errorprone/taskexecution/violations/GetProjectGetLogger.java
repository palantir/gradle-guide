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
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.util.ASTHelpers;
import com.palantir.gradle.guide.errorprone.utils.Tasks;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.Optional;

public final class GetProjectGetLogger implements TaskExecutionViolation {
    /** Matches {@code task.getProject().getLogger()}. */
    @Override
    public boolean matches(MethodInvocationTree illegalCall, VisitorState state) {

        Optional<ExpressionTree> receiverMaybe = Optional.ofNullable(ASTHelpers.getReceiver(illegalCall));
        boolean receiverIsMethodCall = receiverMaybe
                .map(receiver -> receiver instanceof MethodInvocationTree)
                .orElse(false);
        if (receiverIsMethodCall) {
            MethodInvocationTree receiverCall = (MethodInvocationTree) receiverMaybe.get();
            return Tasks.TASK_GET_PROJECT.matches(receiverCall, state)
                    && Tasks.PROJECT_GET_LOGGER.matches(illegalCall, state);
        }

        return false;
    }

    /** Fixes {@code task.getProject().getLogger() to task.getLogger()}. */
    @Override
    public void fixOrReport(MethodInvocationTree illegalCall, VisitorState state, BugChecker bugchecker) {
        MethodInvocationTree getProject = (MethodInvocationTree) ASTHelpers.getReceiver(illegalCall);
        Optional<String> receiverSource =
                Optional.ofNullable(ASTHelpers.getReceiver(getProject)).map(state::getSourceForNode);
        String simplifiedCall = receiverSource.map(receiver -> receiver + ".").orElse("") + "getLogger()";

        state.reportMatch(bugchecker
                .buildDescription(illegalCall)
                .addFix(SuggestedFix.replace(illegalCall, simplifiedCall))
                .setMessage("Instead of `getProject().getLogger()`, just do `getLogger()`")
                .build());
    }
}
