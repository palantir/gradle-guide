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

package com.palantir.gradle.guide.errorprone.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NameUtilsTest {
    @Nested
    class EndsWith {
        @Test
        void true_when_is_suffix() {
            assertThat(NameUtils.endsWith("something", "thing")).isTrue();
        }

        @Test
        void false_when_not_suffix() {
            assertThat(NameUtils.endsWith("something", "blah")).isFalse();
        }

        @Test
        void true_when_suffix_is_entire_string() {
            assertThat(NameUtils.endsWith("thing", "thing")).isTrue();
        }

        @Test
        void false_when_suffix_longer_than_string() {
            assertThat(NameUtils.endsWith("ing", "thing")).isFalse();
        }

        @Test
        void true_when_suffix_is_empty() {
            assertThat(NameUtils.endsWith("something", "")).isTrue();
        }

        @Test
        void false_when_name_is_empty_but_suffix_is_not() {
            assertThat(NameUtils.endsWith("", "thing")).isFalse();
        }

        @Test
        void true_when_both_empty() {
            assertThat(NameUtils.endsWith("", "")).isTrue();
        }
    }
}
