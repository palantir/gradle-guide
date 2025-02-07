/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 */
package com.palantir.gradle.guide.internal.toc;

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

        String correctedReadme = TableOfContentsGenerator.generate(currentReadme, Paths.get("../guide"));

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
