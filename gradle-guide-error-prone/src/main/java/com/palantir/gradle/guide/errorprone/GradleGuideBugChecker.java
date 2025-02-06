/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.guide.errorprone;

import com.google.errorprone.bugpatterns.BugChecker;

public abstract class GradleGuideBugChecker extends BugChecker {
    @Override
    public final String linkUrl() {
        return "https://github.com/palantir/gradle-guide#user-content-errorprone:" + canonicalName();
    }

    @Override
    public final String toString() {
        return canonicalName();
    }
}
