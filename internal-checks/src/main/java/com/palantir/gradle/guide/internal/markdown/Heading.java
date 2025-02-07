/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.guide.internal.markdown;

import java.util.Locale;

public record Heading(String text) {
    public Anchor asAnchor() {
        return new Anchor(text.toLowerCase(Locale.ROOT).replace(" ", "-").replaceAll("[/`<>]", ""));
    }

    @Override
    public String toString() {
        return text;
    }
}
