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
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import java.util.List;
import java.util.Optional;

public final class ChainedCall {
    /**
     * To represent {@code foo().bar().baz()}, this list will be [baz, bar, foo]
     * This order facilitates matching in {@code matches}
     */
    private final List<MethodCall> calls;

    private ChainedCall(List<MethodCall> calls) {
        this.calls = calls;
    }

    /** Creates a two-method chain: {@code call1().call2()} */
    public static ChainedCall of(MethodCall call1, MethodCall call2) {
        return new ChainedCall(List.of(call2, call1));
    }

    /** Creates a single method call */
    public static ChainedCall of(MethodCall call) {
        return new ChainedCall(List.of(call));
    }

    public int chainLength() {
        return calls.size();
    }

    /**
     * Tests if this matches the given method invocation tree.
     * Walks down the receiver chain, matching each call in sequence.
     */
    public boolean matches(MethodInvocationTree other, VisitorState state) {
        MethodInvocationTree current = other;

        for (int i = 0; i < calls.size(); i++) {
            MethodCall expected = calls.get(i);
            boolean isInnermost = i == calls.size() - 1;

            if (!expected.matches(current, state)) {
                return false;
            }

            if (isInnermost) {
                return true; // Successfully matched the entire chain
            }

            // Move to the next call in the chain (the receiver)
            Optional<ExpressionTree> otherReceiverMaybe = Optional.ofNullable(ASTHelpers.getReceiver(current));
            if (otherReceiverMaybe.isEmpty() || !(otherReceiverMaybe.get() instanceof MethodInvocationTree)) {
                return false; // Chain ended early - length mismatch
            }

            current = (MethodInvocationTree) otherReceiverMaybe.get();
        }

        throw new IllegalStateException("This codepath should be unreachable");
    }
}
