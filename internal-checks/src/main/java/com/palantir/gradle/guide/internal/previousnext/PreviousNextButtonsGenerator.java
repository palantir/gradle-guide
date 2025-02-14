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

package com.palantir.gradle.guide.internal.previousnext;

import com.palantir.gradle.guide.internal.markdown.TableOfContentsSource;
import java.nio.file.Path;

final class PreviousNextButtonsGenerator {
    public static String previousNextButtons(TableOfContentsSource tableOfContentsSource, Path path, String input) {
        //String previous = tableOfContentsSource.before(path).map(path -> );
        // Optional<Path> next = tableOfContentsSource.after(path);

        //return input + "\n" + previousNextButtons(previous, next);
        return "";
    }
}
