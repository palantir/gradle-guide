/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.guide.checks;

import com.google.errorprone.bugpatterns.BugChecker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class GradlePluginErrorpronesFileIsUpToDateTest {
    private static final boolean CI = System.getenv("CI") != null;

    @Test
    void errorprones_file_is_up_to_date() throws IOException {
        Path errorpronesFile = Paths.get("../gradle-guide/src/main/resources/gradle-guide/errorprones");

        String existing = Files.readString(errorpronesFile);
        String expected = AllErrorprones.allErrorprones()
                .map(BugChecker::canonicalName)
                .sorted()
                .collect(Collectors.joining("\n", "", "\n"));

        if (existing.equals(expected)) {
            return;
        }

        if (!CI) {
            Files.writeString(errorpronesFile, expected);
            return;
        }

        throw new RuntimeException(
                "errorprones file in gradle-guide is not up to date. Please rerun this test locally and commit.");
    }
}
