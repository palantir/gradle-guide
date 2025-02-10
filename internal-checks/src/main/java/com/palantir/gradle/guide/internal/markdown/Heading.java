/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.guide.internal.markdown;

public record Heading(int level, HeadingText text) {
    @Override
    public String toString() {
        return "#".repeat(level) + text.toString();
    }
}
