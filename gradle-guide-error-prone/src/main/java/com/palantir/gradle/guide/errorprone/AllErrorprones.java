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

package com.palantir.gradle.guide.errorprone;

import com.google.errorprone.bugpatterns.BugChecker;
import java.util.ServiceLoader;
import java.util.stream.Stream;

public final class AllErrorprones {
    public static Stream<BugChecker> allErrorprones() {
        return ServiceLoader.load(BugChecker.class).stream().map(ServiceLoader.Provider::get);
    }

    public static Stream<GradleGuideBugChecker> allGradleGuideErrorprones() {
        return allErrorprones()
                .filter(checker -> checker instanceof GradleGuideBugChecker)
                .map(checker -> (GradleGuideBugChecker) checker);
    }

    private AllErrorprones() {}
}
