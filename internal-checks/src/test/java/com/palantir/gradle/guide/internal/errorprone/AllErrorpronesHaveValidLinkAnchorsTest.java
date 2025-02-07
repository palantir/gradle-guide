/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.guide.internal.errorprone;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.gradle.guide.errorprone.GradleGuideBugChecker;
import com.palantir.gradle.guide.errorprone.GradleGuideBugChecker.MoreInfoSubsectionHeaderLink;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class AllErrorpronesHaveValidLinkAnchorsTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("com.palantir.gradle.guide.errorprone.AllErrorprones#allErrorprones")
    void all_have_valid_links_anchors(GradleGuideBugChecker bugChecker) {
        MoreInfoSubsectionHeaderLink moreInfo = (MoreInfoSubsectionHeaderLink) bugChecker.moreInfoLink();

        Path mdFile = Paths.get("../guide").resolve(moreInfo.mdFileName());
        assertThat(mdFile).exists();
    }
}
