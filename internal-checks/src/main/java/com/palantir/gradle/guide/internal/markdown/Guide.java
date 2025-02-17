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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record Guide(Readme readme, Set<MdFile> mdFiles) {
    public MdFile mdFileByFileName(String fileName) {
        return mdFiles.stream()
                .filter(mdFile -> mdFile.path().getFileName().toString().equals(fileName))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException("Could not find markdown file with name %s. Options are %s"
                                .formatted(
                                        fileName,
                                        mdFiles.stream()
                                                .map(mdFile -> mdFile.path()
                                                        .getFileName()
                                                        .toString())
                                                .toList())));
    }

    public static Guide fromRootDirectory(Path rootDir) {
        Path guideDir = rootDir.resolve("guide");

        Set<MdFile> allMdFiles =
                allMdFiles(guideDir).stream().map(MdFile::fromPath).collect(Collectors.toSet());

        Readme readme = Readme.fromPath(rootDir.resolve("README.md"), allMdFiles);

        return new Guide(readme, allMdFiles);
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
}
