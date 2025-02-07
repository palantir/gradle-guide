/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.guide.internal.markdown;

public record Anchor(String anchor) {
    @Override
    public String toString() {
        return anchor;
    }
}
