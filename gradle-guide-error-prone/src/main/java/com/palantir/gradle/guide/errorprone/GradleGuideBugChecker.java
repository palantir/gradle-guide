/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.guide.errorprone;

import com.google.errorprone.bugpatterns.BugChecker;

public abstract class GradleGuideBugChecker extends BugChecker {
    @Override
    public final String linkUrl() {
        return "https://github.com/palantir/gradle-guide/errorprones.md#user-content:" + canonicalName();
    }

    public abstract MoreInfoLink moreInfoLink();

    @Override
    public final String toString() {
        return canonicalName();
    }

    public interface MoreInfoLink {}

    public record MoreInfoSubsectionHeaderLink(String mdFileName, String subsectionHeader) implements MoreInfoLink {}
}
