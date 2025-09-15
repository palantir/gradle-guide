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

public class FixTemplate {

    private String unformatted;
    private int numFormatArgs;

    private FixTemplate(String unformatted, int numFormatArgs) {
        Preconditions.checkArgument(
                0 <= numFormatArgs && numFormatArgs <= 1, "Only 0 or 1 arguments supported at the moment.");

        this.unformatted = unformatted;
        this.numFormatArgs = numFormatArgs;
    }

    private static int numArgs(String format) {
        return format.split("%s", -1).length - 1;
    }

    public String unformatted() {
        return unformatted;
    }

    public int numFormatArgs() {
        return numFormatArgs;
    }

    // Static factory methods returning specific interfaces
    public static FixTemplate nullary(String template) {
        int numArgs = numArgs(template);
        Preconditions.checkArgument(numArgs == 0, "Nullary template cannot have %s arguments", numArgs);
        return new FixTemplate(template, 0);
    }

    public static FixTemplate unary(String template) {
        int numArgs = numArgs(template);
        Preconditions.checkArgument(numArgs == 1, "Unary template cannot have %s arguments", numArgs);
        return new FixTemplate(template, 1);
    }

    public String render(String... args) {
        Preconditions.checkArgument(
                args.length == numFormatArgs,
                "FixTemplate has %s args, but render() received %s args",
                numFormatArgs,
                args.length);
        return String.format(unformatted, (Object[]) args);
    }
}
