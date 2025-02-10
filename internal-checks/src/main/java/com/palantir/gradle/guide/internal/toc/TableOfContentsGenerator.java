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

import com.google.common.collect.Sets;
import com.palantir.gradle.guide.internal.markdown.MdFile;
import com.palantir.gradle.guide.internal.markdown.TableOfContentsSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import one.util.streamex.StreamEx;

final class TableOfContentsGenerator {

    private static final String TOC_START = "<!-- TableOfContents: START -->";
    private static final String TOC_END = "<!-- TableOfContents: END -->";

    public static String generate(String readmeContent, Path guideDir) {
        TableOfContentsSource tocSource = TableOfContentsSource.fromString(guideDir, readmeContent)
                .orElseThrow(() -> new RuntimeException("No TableOfContentsSource found in README.md"));

        Set<Path> mdFilesNotInTableOfContents =
                Sets.difference(allMdFiles(guideDir), new HashSet<>(tocSource.referencedFiles()));

        if (!mdFilesNotInTableOfContents.isEmpty()) {
            throw new RuntimeException(
                    "The following files are not in the table of contents: " + mdFilesNotInTableOfContents);
        }

        String toc = StreamEx.of(tocSource.referencedFiles())
                .zipWith(integers())
                .mapKeyValue((mdFile, i) -> contentsSectionForMdFile(guideDir, mdFile, i))
                .joining("\n");

        int start = readmeContent.indexOf(TOC_START) + TOC_START.length();
        int end = readmeContent.indexOf(TOC_END);

        return readmeContent.substring(0, start) + "\n" + toc + "\n" + readmeContent.substring(end);
    }

    private static String contentsSectionForMdFile(Path guideDir, Path mdFilePath, Integer index) {
        MdFile mdFile = MdFile.fromPath(mdFilePath);
        String top = String.format("%s. [%s](guide/%s)", index, mdFile.title(), guideDir.relativize(mdFilePath));

        String subheadings = StreamEx.of(mdFile.headingsAtLevel(2))
                .zipWith(integers())
                .mapKeyValue((heading, subIndex) -> String.format(
                        "    %d. [%s](guide/%s#%s)",
                        subIndex,
                        heading.text(),
                        guideDir.relativize(mdFilePath),
                        heading.text().asAnchor()))
                .collect(Collectors.joining("\n"));

        return top + "\n" + subheadings;
    }

    private static IntStream integers() {
        return IntStream.iterate(1, i -> i + 1);
    }

    private static Set<Path> allMdFiles(Path guideDir) {
        try (Stream<Path> allMdFiles = Files.list(guideDir)) {
            return allMdFiles
                    .filter(path -> !path.toFile().isDirectory())
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private TableOfContentsGenerator() {}
}
