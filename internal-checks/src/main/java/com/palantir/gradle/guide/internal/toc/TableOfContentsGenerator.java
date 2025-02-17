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
package com.palantir.gradle.guide.internal.toc;

import com.palantir.gradle.guide.internal.markdown.MdFile;
import com.palantir.gradle.guide.internal.markdown.Readme;
import com.palantir.gradle.guide.internal.markdown.TableOfContentsSource;
import com.palantir.gradle.guide.internal.markdown.contentchanger.ContentChanger;
import com.palantir.gradle.guide.internal.markdown.contentchanger.SingleContentChanger;
import com.palantir.gradle.guide.internal.text.SectionTag;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import one.util.streamex.StreamEx;

public final class TableOfContentsGenerator {

    private static final SectionTag TABLE_OF_CONTENTS = new SectionTag("TableOfContents");

    public static ContentChanger generate(Readme readme) {
        return new SingleContentChanger(readme.mdFile(), readmeContent -> {
            TableOfContentsSource tocSource = readme.tableOfContentsSource();

            String toc = StreamEx.of(tocSource.referencedFiles())
                    .zipWith(integers())
                    .mapKeyValue((MdFile mdFile, Integer index) -> contentsSectionForMdFile(readme, mdFile, index))
                    .joining("\n");

            return TABLE_OF_CONTENTS.replaceTaggedSection(readmeContent, toc);
        });
    }

    private static String contentsSectionForMdFile(Readme readme, MdFile mdFile, Integer index) {
        String top = String.format("%d. %s", index, readme.mdFile().markdownLinkTo(mdFile));

        String subheadings = StreamEx.of(mdFile.headingsAtLevel(2))
                .zipWith(integers())
                .mapKeyValue((heading, subIndex) ->
                        String.format("    %d. %s", subIndex, readme.mdFile().markdownLinkTo(heading)))
                .collect(Collectors.joining("\n"));

        return top + "\n" + subheadings;
    }

    private static IntStream integers() {
        return IntStream.iterate(1, i -> i + 1);
    }

    private TableOfContentsGenerator() {}
}
