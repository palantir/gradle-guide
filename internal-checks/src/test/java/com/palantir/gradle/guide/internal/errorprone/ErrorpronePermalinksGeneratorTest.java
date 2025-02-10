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
