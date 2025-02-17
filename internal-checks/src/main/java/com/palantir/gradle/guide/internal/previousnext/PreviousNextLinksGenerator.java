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

import com.palantir.gradle.guide.internal.markdown.MdFile;
import com.palantir.gradle.guide.internal.markdown.Readme;
import com.palantir.gradle.guide.internal.markdown.contentchanger.ContentChanger;
import com.palantir.gradle.guide.internal.markdown.contentchanger.SingleContentChanger;
import com.palantir.gradle.guide.internal.text.SectionTag;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class PreviousNextLinksGenerator {
    private static final SectionTag PREVIOUS_NEXT = new SectionTag("PreviousNext");

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

            @SuppressWarnings("for-rollout:StreamFlatMapOptional")
            String tds = Stream.of(previous, Optional.of(tableOfContents), next)
                    .flatMap(Optional::stream)
                    .map(text -> "  " + text)
                    .collect(Collectors.joining("\n"));

            String table = String.join(
                    "\n", PREVIOUS_NEXT.startTag(), "<table><tr>", tds, "</tr></table>", PREVIOUS_NEXT.endTag());

            return String.join(
                            "\n",
                            table,
                            "",
                            PREVIOUS_NEXT.removeExistingTaggedSectionsAndPreceedingWhitespace(input),
                            "",
                            table)
                    + "\n";
        });
    }

    private PreviousNextLinksGenerator() {}
}
