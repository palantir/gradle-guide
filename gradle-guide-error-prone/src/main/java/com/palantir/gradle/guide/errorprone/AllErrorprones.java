/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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
