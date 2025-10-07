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

package com.palantir.gradle.guide.errorprone.laziness;

import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.palantir.gradle.guide.errorprone.utils.ChainedCallMatcher;
import com.sun.source.tree.MethodInvocationTree;
import java.util.Optional;

/**
 * Represents strategies to report or auto-fix illegal methods and methods chained to it.
 */
public record EagerApiUsage(ChainedCallMatcher violationMatcher, String message, Optional<FixFactory> fix) {
    public static EagerApiUsage fix(ChainedCallMatcher violationMatcher, String messag, FixFactory fix) {
        return new EagerApiUsage(violationMatcher, messag, Optional.of(fix));
    }

    public static EagerApiUsage report(ChainedCallMatcher violationMatcher, String message) {
        return new EagerApiUsage(violationMatcher, message, Optional.empty());
    }

    public boolean matches(MethodInvocationTree call, VisitorState state) {
        return violationMatcher.matches(call, state);
    }

    /**
     * Returns {@code true} if a fix or report was done for {@code illegalCall}.
     * Note that this is called on every method invocation, so performance matters.
     */
    public void fixOrReport(MethodInvocationTree illegalCall, VisitorState state, BugChecker bugChecker) {
        Description.Builder descriptionBuilder =
                bugChecker.buildDescription(illegalCall).setMessage(message);

        SuggestedFix renderedFix =
                fix.map(fixFactory -> fixFactory.construct(illegalCall, state)).orElseGet(SuggestedFix::emptyFix);

        state.reportMatch(descriptionBuilder.addFix(renderedFix).build());
    }
}
