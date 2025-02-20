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

import com.palantir.gradle.guide.errorprone.ConfigurationAvoidanceRegistration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ErrorpronePermalinksGeneratorTest {
    @Test
    void check_a_correct_errorprones_md_is_created() {
        assertThat(ErrorpronePermalinksGenerator.generate(Set.of(new ConfigurationAvoidanceRegistration())))
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

                <a id="ConfigurationAvoidanceRegistration">`ConfigurationAvoidanceRegistration`</a>

                </td>
                <td>
                When registering a new `Task`, `Configuration` or other Gradle domain type, use `.register` instead of `.create` to avoid realising the object eagerly and performing unnecessary work which will slow down the build.
                </td>
                <td>
                <a href="guide/avoiding-unnecessary-configuration.md#lazy-task-registration">More Info</a>
                </td>
                </tr>
                </tbody>
                </table>
                """);
    }
}
