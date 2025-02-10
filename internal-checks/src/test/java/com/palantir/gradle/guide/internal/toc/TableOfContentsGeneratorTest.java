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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TableOfContentsGeneratorTest {
    @Test
    void generates_a_good_table_of_contents(@TempDir Path guideDir) throws IOException {
        Files.writeString(
                guideDir.resolve("starting-stuff.md"),
                """
            # Starting Stuff

            ## Subheading 1

            ### Ignore me, I'm too low level

            ## Subheading with `code` elements/slashes <>
            """);

        Files.writeString(
                guideDir.resolve("more-stuff.md"),
                """
            # More Stuff

            ## Subheading 1

            ## Subheading 2

            #### Really ignore me
            """);

        // language=markdown
        String readme =
                """
            # Title

            <!-- TableOfContentsSource:
            * starting-stuff.md
            * more-stuff.md
            -->

            <!-- TableOfContents: START -->
            some other stuff that was here
            <!-- TableOfContents: END -->

            other stuff afterwards
            """;

        String newReadme = TableOfContentsGenerator.generate(readme, guideDir);

        // language=markdown
        assertThat(newReadme)
                .isEqualTo(
                        """
            # Title

            <!-- TableOfContentsSource:
            * starting-stuff.md
            * more-stuff.md
            -->

            <!-- TableOfContents: START -->
            1. [Starting Stuff](guide/starting-stuff.md)
                1. [Subheading 1](guide/starting-stuff.md#subheading-1)
                2. [Subheading with `code` elements/slashes <>](guide/starting-stuff.md\
            #subheading-with-code-elementsslashes-)
            2. [More Stuff](guide/more-stuff.md)
                1. [Subheading 1](guide/more-stuff.md#subheading-1)
                2. [Subheading 2](guide/more-stuff.md#subheading-2)
            <!-- TableOfContents: END -->

            other stuff afterwards
            """);
    }
}
