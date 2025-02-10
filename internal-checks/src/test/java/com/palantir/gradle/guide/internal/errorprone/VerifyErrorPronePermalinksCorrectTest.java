/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 */
package com.palantir.gradle.guide.internal.errorprone;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

final class VerifyErrorPronePermalinksCorrectTest {
    private static final boolean CI = System.getenv("CI") != null;

    @Test
    void verify_errorprone_permalinks_are_correct() throws IOException {
        Path errorpronesMd = Paths.get("../errorprones.md");

        String currentErrorprones = Files.readString(errorpronesMd);

        String correctedErrorprones = ErrorpronePermalinksGenerator.generate();

        if (currentErrorprones.equals(correctedErrorprones)) {
            return;
        }

        if (!CI) {
            Files.writeString(errorpronesMd, correctedErrorprones);
            return;
        }

        throw new RuntimeException("errorprones.md is not up to date. Please rerun this test locally and commit.");
    }
}
