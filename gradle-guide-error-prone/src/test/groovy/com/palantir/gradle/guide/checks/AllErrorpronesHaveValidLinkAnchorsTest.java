/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.guide.checks;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.errorprone.bugpatterns.BugChecker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class AllErrorpronesHaveValidLinkAnchorsTest {
    private static String readmeContent;

    @BeforeAll
    static void beforeAll() throws IOException {
        readmeContent = Files.readString(Paths.get("../README.md"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("errorproneNames")
    void all_have_valid_links_anchors(String name) {
        assertThat(readmeContent).contains(String.format("<a id=\"errorprone:%s\" />", name));
    }

    static Stream<String> errorproneNames() {
        return AllErrorprones.allErrorprones().map(BugChecker::canonicalName);
    }
}
