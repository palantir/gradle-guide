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

package com.palantir.gradle.guide.internal.markdown;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record TableOfContentsSource(List<Path> referencedFiles) {
    private static final String TOC_SOURCE_START = "<!-- TableOfContentsSource:";
    private static final String TOC_SOURCE_END = "-->";

    public static Optional<TableOfContentsSource> fromString(Path guideDir, String readme) {
        List<String> lines = readme.lines()
                .dropWhile(line -> !line.contains(TOC_SOURCE_START))
                .skip(1)
                .takeWhile(line -> !line.contains(TOC_SOURCE_END))
                .toList();

        if (lines.isEmpty()) {
            return Optional.empty();
        }

        List<Path> mdFilesInContents = lines.stream()
                .map(line -> line.replace("* ", ""))
                .map(guideDir::resolve)
                .toList();

        List<Path> mdFilesThatDontExist =
                mdFilesInContents.stream().filter(Files::notExists).toList();

        if (!mdFilesThatDontExist.isEmpty()) {
            throw new RuntimeException(
                    "The following files in the table of contents do not exist: " + mdFilesThatDontExist);
        }

        return Optional.of(new TableOfContentsSource(mdFilesInContents));
    }

    public Optional<Path> before(Path path) {
        int index = indexOf(path);

        if (index == 0) {
            return Optional.empty();
        }

        return Optional.of(referencedFiles.get(index - 1));
    }

    public Optional<Path> after(Path path) {
        int index = indexOf(path);

        if (index >= referencedFiles.size() - 1) {
            return Optional.empty();
        }

        return Optional.of(referencedFiles.get(index + 1));
    }

    private int indexOf(Path path) {
        int index = referencedFiles.indexOf(path);

        if (index == -1) {
            throw new IllegalStateException("Path not in table of contents: " + path);
        }
        return index;
    }
}
