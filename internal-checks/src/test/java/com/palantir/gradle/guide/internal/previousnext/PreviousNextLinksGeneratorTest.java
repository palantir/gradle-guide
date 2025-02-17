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

final class PreviousNextLinksGeneratorTest {
    @Test
    void generates_previous_next_buttons_at_top_and_bottom(@TempDir Path rootDir) throws IOException {
        Files.writeString(
                rootDir.resolve("README.md"),
                """
            # Readme
            ## Table of Contents
            <!-- TableOfContentsSource:
            first.md
            second.md
            third.md
            -->
            """);

        Path guideDir = rootDir.resolve("guide");
        Files.createDirectories(guideDir);

        Path firstMd = guideDir.resolve("first.md");
        Files.writeString(
                firstMd,
                """
            # First

            <!-- PreviousNext:START -->
            Some old stuff
            <!-- PreviousNext:END -->
            """);

        Path secondMd = guideDir.resolve("second.md");
        Files.writeString(
                secondMd,
                """
            <!-- PreviousNext:START -->
            Some old stuff with lots of whitespace after
            <!-- PreviousNext:END -->




            # Second
            """);

        Path thirdMd = guideDir.resolve("third.md");
        Files.writeString(thirdMd, """
            # Third
            """);

        Guide.fromRootDirectory(rootDir).previousNextLinks().changeContent();

        assertThat(firstMd)
                .hasContent(
                        """
            <!-- PreviousNext:START -->
            <table><tr>
              <td align="center"><a href="../README.md#table-of-contents">Table of Contents</a></td>
              <td align="right">Next: <a href="second.md">Second</a></td>
            </tr></table>
            <!-- PreviousNext:END -->

            # First

            <!-- PreviousNext:START -->
            <table><tr>
              <td align="center"><a href="../README.md#table-of-contents">Table of Contents</a></td>
              <td align="right">Next: <a href="second.md">Second</a></td>
            </tr></table>
            <!-- PreviousNext:END -->
            """);

        assertThat(secondMd)
                .hasContent(
                        """
            <!-- PreviousNext:START -->
            <table><tr>
              <td>Previous: <a href="first.md">First</a></td>
              <td align="center"><a href="../README.md#table-of-contents">Table of Contents</a></td>
              <td align="right">Next: <a href="third.md">Third</a></td>
            </tr></table>
            <!-- PreviousNext:END -->

            # Second


            <!-- PreviousNext:START -->
            <table><tr>
              <td>Previous: <a href="first.md">First</a></td>
              <td align="center"><a href="../README.md#table-of-contents">Table of Contents</a></td>
              <td align="right">Next: <a href="third.md">Third</a></td>
            </tr></table>
            <!-- PreviousNext:END -->
            """);

        assertThat(thirdMd)
                .hasContent(
                        """
            <!-- PreviousNext:START -->
            <table><tr>
              <td>Previous: <a href="second.md">Second</a></td>
              <td align="center"><a href="../README.md#table-of-contents">Table of Contents</a></td>
            </tr></table>
            <!-- PreviousNext:END -->

            # Third


            <!-- PreviousNext:START -->
            <table><tr>
              <td>Previous: <a href="second.md">Second</a></td>
              <td align="center"><a href="../README.md#table-of-contents">Table of Contents</a></td>
            </tr></table>
            <!-- PreviousNext:END -->
            """);
    }
}
