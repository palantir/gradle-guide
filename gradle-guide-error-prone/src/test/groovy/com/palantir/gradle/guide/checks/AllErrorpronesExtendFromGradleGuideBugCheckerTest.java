/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.guide.checks;

import com.google.errorprone.bugpatterns.BugChecker;
import com.palantir.gradle.guide.errorprone.GradleGuideBugChecker;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class AllErrorpronesExtendFromGradleGuideBugCheckerTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("com.palantir.gradle.guide.errorprone.AllErrorprones#allErrorprones")
    void all_extend_from_gradle_guide_bug_checker(BugChecker bugChecker) {
        Assertions.assertThat(bugChecker).isInstanceOf(GradleGuideBugChecker.class);
    }
}
