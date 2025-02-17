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

import com.palantir.gradle.guide.internal.markdown.ContentChanger;
import com.palantir.gradle.guide.internal.markdown.MdFile;
import com.palantir.gradle.guide.internal.markdown.TableOfContentsSource;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class PreviousNextButtonsGenerator {
    public static ContentChanger previousNextButtons(TableOfContentsSource tableOfContentsSource, MdFile mdFile) {
        return new ContentChanger(mdFile, input -> {
            Optional<String> previous = tableOfContentsSource
                    .before(mdFile)
                    .map(previousMdFile -> "Previous: " + mdFile.markdownLinkTo(previousMdFile));

            Optional<String> next =
                    tableOfContentsSource.after(mdFile).map(nextMdFile -> "Next: " + mdFile.markdownLinkTo(nextMdFile));

            String previousNextSpans = Stream.of(previous, next)
                    .flatMap(Optional::stream)
                    .map(text -> "<span>" + text + "</span>")
                    .collect(Collectors.joining(""));

            String inDiv =
                    "<div style=\"display: flex; justify-content: space-between;\">" + previousNextSpans + "</div>";

            return previousNextSpans + "\n\n" + input;
        });
    }

    private PreviousNextButtonsGenerator() {}
}
