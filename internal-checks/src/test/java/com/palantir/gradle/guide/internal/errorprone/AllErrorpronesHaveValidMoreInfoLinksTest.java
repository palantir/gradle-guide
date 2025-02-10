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
