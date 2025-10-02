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

package com.palantir.gradle.guide.errorprone.types;

import com.google.common.base.Preconditions;
import com.google.errorprone.VisitorState;
import com.palantir.gradle.guide.errorprone.types.Replacement.LiteralReplacement;
import com.palantir.gradle.guide.errorprone.types.Replacement.TemplatedReplacement;
import com.sun.source.tree.MethodInvocationTree;
import java.util.stream.Collectors;

public abstract sealed class Replacement permits LiteralReplacement, TemplatedReplacement {

    static final class LiteralReplacement extends Replacement {
        private final String literal;

        LiteralReplacement(String literal) {
            this.literal = literal;
        }

        @Override
        public String renderCall(MethodInvocationTree tree, VisitorState state) {
            Preconditions.checkArgument(tree.getArguments().isEmpty());
            return literal;
        }
    }

    static final class TemplatedReplacement extends Replacement {
        private final String template;

        TemplatedReplacement(String template) {
            this.template = template;
        }

        @Override
        public String renderCall(MethodInvocationTree tree, VisitorState state) {
            Preconditions.checkArgument(!tree.getArguments().isEmpty());
            String args =
                    tree.getArguments().stream().map(state::getSourceForNode).collect(Collectors.joining(", "));
            return String.format(template, args);
        }
    }

    protected abstract String renderCall(MethodInvocationTree tree, VisitorState state);

    final String render(String receiver, MethodInvocationTree tree, VisitorState state) {
        if (receiver.isEmpty()) {
            return renderCall(tree, state);
        }
        return receiver + "." + renderCall(tree, state);
    }

    private static int numReplacements(String format) {
        return format.split("%s", -1).length - 1;
    }

    public static Replacement literal(String template) {
        int numArgs = numReplacements(template);
        Preconditions.checkArgument(numArgs == 0, "LiteralReplacement template cannot have %s arguments", numArgs);
        return new LiteralReplacement(template);
    }

    public static Replacement template(String template) {
        int numArgs = numReplacements(template);
        Preconditions.checkArgument(numArgs == 1, "TemplatedReplacement template cannot have %s arguments", numArgs);
        return new TemplatedReplacement(template);
    }
}
