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

import com.palantir.gradle.guide.internal.markdown.Guide;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

final class VerifyTableOfContentsCorrectTest {
    private static final boolean CI = System.getenv("CI") != null;

    @Test
    void verify_table_of_contents_is_correct() throws IOException {
        Path readme = Paths.get("../README.md");

        String currentReadme = Files.readString(readme);

        String correctedReadme = TableOfContentsGenerator.generate(
                Guide.fromRootDirectory(Paths.get("..")).readme());

        if (currentReadme.equals(correctedReadme)) {
            return;
        }

        if (!CI) {
            Files.writeString(readme, correctedReadme);
            return;
        }

        throw new RuntimeException("README.md is not up to date. Please rerun this test locally and commit.");
    }
}
