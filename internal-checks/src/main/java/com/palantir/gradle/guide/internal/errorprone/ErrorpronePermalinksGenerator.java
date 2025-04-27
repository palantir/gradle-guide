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

import com.google.errorprone.BugPattern.SeverityLevel;
import com.palantir.gradle.guide.errorprone.GradleGuideBugChecker;
import com.palantir.gradle.guide.errorprone.GradleGuideBugChecker.MoreInfoHeadingLink;
import com.palantir.gradle.guide.errorprone.GradleGuideBugChecker.MoreInfoLink;
import com.palantir.gradle.guide.errorprone.GradleGuideBugChecker.MoreInfoPageLink;
import com.palantir.gradle.guide.errorprone.utils.AllErrorprones;
import com.palantir.gradle.guide.internal.markdown.HeadingText;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

final class ErrorpronePermalinksGenerator {
    public static String generate(Set<GradleGuideBugChecker> gradleGuideBugCheckers) {
        String prefix =
                """
            # gradle-guide Error Prone Permalinks

            <table>
            <thead>
            <tr>
            <td>Name</td>
            <td>Detailed Link</td>
            <td>Description</td>
            </tr>
            </thead>
            <tbody>
            """;

        String middle = gradleGuideBugCheckers.stream()
                // SUGGESTION is used for refactorings only, don't need docs for it
                .filter(bugChecker -> !bugChecker.defaultSeverity().equals(SeverityLevel.SUGGESTION))
                .sorted(Comparator.comparing(GradleGuideBugChecker::canonicalName))
                .map(bugChecker -> {
                    return """
                            <tr>
                            <td>

                            <a id="$NAME" href="guide/$LINK">`$NAME`</a>

                            </td>
                            <td>
                            <a href="guide/$LINK">Please read</a>
                            </td>
                            <td>

                            $MESSAGE

                            </td>
                            </tr>
                            """
                            .replace("$NAME", bugChecker.canonicalName())
                            .replace("$MESSAGE", bugChecker.message())
                            .replace("$LINK", renderMoreInfoLink(bugChecker.moreInfoLink()));
                })
                .collect(Collectors.joining("\n"));

        String suffix = """
            </tbody>
            </table>
            """;

        return prefix + middle + suffix;
    }

    private static String renderMoreInfoLink(MoreInfoLink moreInfoLink) {
        if (moreInfoLink instanceof MoreInfoPageLink pageLink) {
            return pageLink.mdFileName();
        } else if (moreInfoLink instanceof MoreInfoHeadingLink headingLink) {
            return headingLink.mdFileName() + "#" + new HeadingText(headingLink.heading()).asAnchor();
        }

        throw new UnsupportedOperationException(
                "Cannot render " + moreInfoLink.getClass().getSimpleName());
    }

    public static String generate() {
        return generate(AllErrorprones.allGradleGuideErrorprones().collect(Collectors.toSet()));
    }

    private ErrorpronePermalinksGenerator() {}
}
