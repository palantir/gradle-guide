/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.guide.internal.errorprone;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.gradle.guide.errorprone.RegisterInsteadOfCreate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ErrorpronePermalinksGeneratorTest {
    @Test
    void check_a_correct_errorprones_md_is_created() {
        assertThat(ErrorpronePermalinksGenerator.generate(Set.of(new RegisterInsteadOfCreate())))
                .isEqualTo(
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
                <tr>
                <td>

                <a id="RegisterInsteadOfCreate">`RegisterInsteadOfCreate`</a>

                </td>
                <td>
                Don't do this yo
                </td>
                <td>
                <a href="guide/diagnosing-build-performance.md#configuration-subsection">More Info</a>
                </td>
                </tr>
                </tbody>
                </table>
                """);
    }
}
