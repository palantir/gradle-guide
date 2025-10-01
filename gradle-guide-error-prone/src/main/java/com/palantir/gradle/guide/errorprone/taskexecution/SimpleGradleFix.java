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
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MethodInvocationTree;
import java.util.Optional;

/**
 * An autofix for a Gradle task that doesn't require service injection.
 * @param replacement Replaces the violating chained call
 */
public record SimpleGradleFix(Replacement replacement) implements GradleFix {
    public boolean worksWithActions() {
        return false;
    }

    public static SimpleGradleFix of(Replacement replacement) {
        return new SimpleGradleFix(replacement);
    }

    public SuggestedFix render(GradleFixContext context, VisitorState state) {
        SuggestedFix.Builder fixBuilder = SuggestedFix.builder();

        String fixedCallChain = replacement.render(context.illegalCallToReplace(), state);

        MethodInvocationTree innermost = context.illegalCallToReplace();
        for (int i = 0; i < context.violatingChainLength() - 1; ++i) {
            innermost = (MethodInvocationTree) ASTHelpers.getReceiver(innermost);
        }

        Optional<String> receiverSource =
                Optional.ofNullable(ASTHelpers.getReceiver(innermost)).map(state::getSourceForNode);
        String correctedCallWithReceiver =
                receiverSource.map(receiver -> receiver + ".").orElse("") + fixedCallChain;
        fixBuilder.replace(context.illegalCallToReplace(), correctedCallWithReceiver);

        return fixBuilder.build();
    }
}
