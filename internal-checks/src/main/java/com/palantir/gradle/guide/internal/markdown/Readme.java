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

public record Readme(Path path, String content, TableOfContentsSource tableOfContentsSource) {
    public String markdownLinkTo(LinkTarget linkTarget) {
        return linkTarget.linkTarget().markdownLinkFrom(path);
    }

    public static Readme fromPath(Path readme, Set<MdFile> mdFiles) {
        try {
            String readmeContent = Files.readString(readme);
            return new Readme(
                    readme,
                    readmeContent,
                    TableOfContentsSource.fromString(mdFiles, readmeContent)
                            .orElseThrow(() -> new IllegalStateException("The readme must have a table of contents")));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
