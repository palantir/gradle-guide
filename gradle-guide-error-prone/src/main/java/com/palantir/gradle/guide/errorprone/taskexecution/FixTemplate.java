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

import com.google.common.base.Preconditions;
import com.palantir.gradle.guide.errorprone.taskexecution.FixTemplate.Nullary;
import com.palantir.gradle.guide.errorprone.taskexecution.FixTemplate.Unary;

public sealed interface FixTemplate permits Nullary, Unary {

    record Nullary(String fix) implements FixTemplate {}

    record Unary(String unformatted) implements FixTemplate {
        public String render(String replacement) {
            return String.format(unformatted, replacement);
        }
    }

    private static int numArgs(String format) {
        return format.split("%s", -1).length - 1;
    }

    static FixTemplate nullary(String template) {
        int numArgs = numArgs(template);
        Preconditions.checkArgument(numArgs == 0, "Nullary template cannot have %s arguments", numArgs);
        return new Nullary(template);
    }

    static FixTemplate unary(String template) {
        int numArgs = numArgs(template);
        Preconditions.checkArgument(numArgs == 1, "Unary template cannot have %s arguments", numArgs);
        return new Unary(template);
    }
}
