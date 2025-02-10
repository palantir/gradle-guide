/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.guide.internal.errorprone;

import com.palantir.gradle.guide.errorprone.AllErrorprones;
import com.palantir.gradle.guide.errorprone.GradleGuideBugChecker;
import com.palantir.gradle.guide.errorprone.GradleGuideBugChecker.MoreInfoHeadingLink;
import com.palantir.gradle.guide.internal.markdown.HeadingText;
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
            <td>Description</td>
            <td>Detailed Link</td>
            </tr>
            </thead>
            <tbody>
            """;

        String middle = gradleGuideBugCheckers.stream()
                .map(bugChecker -> {
                    MoreInfoHeadingLink moreInfoHeadingLink = (MoreInfoHeadingLink) bugChecker.moreInfoLink();
                    return """
                            <tr>
                            <td>

                            <a id="$NAME">`$NAME`</a>

                            </td>
                            <td>
                            $MESSAGE
                            </td>
                            <td>
                            <a href="guide/$LINK">More Info</a>
                            </td>
                            </tr>
                            """
                            .replace("$NAME", bugChecker.canonicalName())
                            .replace("$MESSAGE", bugChecker.message())
                            .replace(
                                    "$LINK",
                                    moreInfoHeadingLink.mdFileName() + "#"
                                            + new HeadingText(moreInfoHeadingLink.heading()).asAnchor());
                })
                .collect(Collectors.joining("\n"));

        String suffix = """
            </tbody>
            </table>
            """;

        return prefix + middle + suffix;
    }

    public static String generate() {
        return generate(AllErrorprones.allGradleGuideErrorprones().collect(Collectors.toSet()));
    }

    private ErrorpronePermalinksGenerator() {}
}
