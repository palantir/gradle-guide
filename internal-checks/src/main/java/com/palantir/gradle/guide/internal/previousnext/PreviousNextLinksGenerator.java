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

import com.palantir.gradle.guide.internal.TextUtils;
import com.palantir.gradle.guide.internal.markdown.MdFile;
import com.palantir.gradle.guide.internal.markdown.Readme;
import com.palantir.gradle.guide.internal.markdown.contentchanger.ContentChanger;
import com.palantir.gradle.guide.internal.markdown.contentchanger.SingleContentChanger;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class PreviousNextLinksGenerator {
    public static ContentChanger previousNextLinks(Readme readme, MdFile mdFile) {
        return new SingleContentChanger(mdFile, input -> {
            Optional<String> previous = readme.tableOfContentsSource()
                    .before(mdFile)
                    .map(previousMdFile -> "<td>Previous: " + mdFile.htmlLinkTo(previousMdFile) + "</td>");

            String tableOfContents = "<td align=\"center\">"
                    + mdFile.htmlLinkTo(readme.mdFile().headingWithText("Table of Contents")) + "</td>";

            Optional<String> next = readme.tableOfContentsSource()
                    .after(mdFile)
                    .map(nextMdFile -> "<td align=\"right\">Next: " + mdFile.htmlLinkTo(nextMdFile) + "</td>");

            String tds = Stream.of(previous, Optional.of(tableOfContents), next)
                    .flatMap(Optional::stream)
                    .map(text -> "  " + text)
                    .collect(Collectors.joining("\n"));

            String table =
                    "<!-- PreviousNext:START -->\n<table><tr>\n" + tds + "\n</tr></table>\n<!-- PreviousNext:END -->";

            return table + "\n\n" + TextUtils.removeExistingTaggedSectionAndPreceedingWhitespace("PreviousNext", input)
                    + "\n\n" + table + "\n";
        });
    }

    private PreviousNextLinksGenerator() {}
}
