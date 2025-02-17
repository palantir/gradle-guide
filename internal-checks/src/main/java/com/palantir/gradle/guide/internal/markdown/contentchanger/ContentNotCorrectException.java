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

package com.palantir.gradle.guide.internal.markdown.contentchanger;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

final class ContentNotCorrectException extends RuntimeException {
    private final Set<Path> mdFilePaths;

    ContentNotCorrectException(Set<Path> mdFilePaths) {
        super("Markdown files are not up to date. Please rerun this test locally and commit. Files not correct:\n\n%s"
                .formatted(mdFilePaths.stream().map(path -> "* " + path).collect(Collectors.joining("\n"))));
        this.mdFilePaths = mdFilePaths;
    }

    ContentNotCorrectException(Path mdFilePath) {
        this(Set.of(mdFilePath));
    }

    public Set<Path> mdFilePaths() {
        return mdFilePaths;
    }
}
