/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.guide.checks;

import com.google.errorprone.bugpatterns.BugChecker;
import java.util.ServiceLoader;
import java.util.stream.Stream;

final class AllErrorprones {
    public static Stream<BugChecker> allErrorprones() {
        return ServiceLoader.load(BugChecker.class).stream().map(ServiceLoader.Provider::get);
    }

    private AllErrorprones() {}
}
