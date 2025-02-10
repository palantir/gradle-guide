/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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
