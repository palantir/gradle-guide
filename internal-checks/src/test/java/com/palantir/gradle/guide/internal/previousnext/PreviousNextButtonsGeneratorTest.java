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

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.gradle.guide.internal.markdown.Guide;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PreviousNextButtonsGeneratorTest {
    @Test
    void generates_previous_next_buttons_at_top_and_bottom(@TempDir Path rootDir) throws IOException {
        Files.writeString(
                rootDir.resolve("README.md"),
                """
            <!-- TableOfContentsSource:
            first.md
            second.md
            third.md
            -->
            """);

        Path guideDir = rootDir.resolve("guide");
        Files.createDirectories(guideDir);

        Path firstMd = guideDir.resolve("first.md");
        Files.writeString(firstMd, """
            # First
            """);

        Path secondMd = guideDir.resolve("second.md");
        Files.writeString(secondMd, """
            # Second
            """);

        Path thirdMd = guideDir.resolve("third.md");
        Files.writeString(thirdMd, """
            # Third
            """);

        Guide guide = Guide.fromRootDirectory(rootDir);

        PreviousNextButtonsGenerator.previousNextButtons(
                        guide.readme().tableOfContentsSource(), guide.mdFileByFileName("first.md"))
                .changeContent();

        assertThat(Files.readString(firstMd))
                .isEqualTo("""
            <span>Next: [Second](second.md)</span>

            # First
            """);

        assertThat(Files.readString(secondMd))
                .isEqualTo("""
            <span>Next: [Second](second.md)</span>

            # First
            """);

        assertThat(Files.readString(thirdMd))
                .isEqualTo("""
            <span>Next: [Second](second.md)</span>

            # First
            """);
    }
}
