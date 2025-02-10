/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.guide.internal.errorprone;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.gradle.guide.errorprone.GradleGuideBugChecker;
import com.palantir.gradle.guide.errorprone.GradleGuideBugChecker.MoreInfoHeadingLink;
import com.palantir.gradle.guide.internal.markdown.MdFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class AllErrorpronesHaveValidMoreInfoLinksTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("com.palantir.gradle.guide.errorprone.AllErrorprones#allErrorprones")
    void all_have_valid_more_info_links(GradleGuideBugChecker bugChecker) {
        MoreInfoHeadingLink moreInfo = (MoreInfoHeadingLink) bugChecker.moreInfoLink();

        Path mdFile = Paths.get("../guide").resolve(moreInfo.mdFileName());
        assertThat(mdFile)
                .describedAs(String.format(
                        "The md file '%s' linked by the %s#moreInfoLink() method exists",
                        mdFile, bugChecker.canonicalName()))
                .exists();

        assertThat(MdFile.fromPath(mdFile).headingsAsStrings())
                .describedAs(String.format(
                        "The heading '%s' linked by %s#moreInfoLink() exists in the md file '%s'",
                        moreInfo.heading(), bugChecker.canonicalName(), mdFile))
                .contains(moreInfo.heading());
    }
}
