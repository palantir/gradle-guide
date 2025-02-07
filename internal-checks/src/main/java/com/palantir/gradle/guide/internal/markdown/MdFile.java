/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.guide.internal.markdown;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import one.util.streamex.StreamEx;

public record MdFile(List<Heading> headings) {
    private static final Pattern SUBHEADING_PATTERN = Pattern.compile("^## (.+)$");

    public static MdFile fromString(String content) {
        return new MdFile(StreamEx.of(content.lines())
                .flatMap(line -> SUBHEADING_PATTERN.matcher(line).results())
                .map(matchResult -> new Heading(matchResult.group(1)))
                .toList());
    }

    public static MdFile fromPath(Path mdFile) {
        try {
            return fromString(Files.readString(mdFile));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
