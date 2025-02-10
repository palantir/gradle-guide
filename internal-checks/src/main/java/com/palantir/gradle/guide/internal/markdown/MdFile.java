/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.guide.internal.markdown;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import one.util.streamex.StreamEx;

public final class MdFile {
    private static final Pattern SUBHEADING_PATTERN = Pattern.compile("^(#+) (.+)$");

    private final String title;
    private final List<Heading> headings;

    public MdFile(String name, List<Heading> headings) {
        this.headings = headings;

        this.title = headings.stream()
                .findFirst()
                .filter(heading -> heading.level() == 1)
                .map(Heading::text)
                .map(HeadingText::text)
                .orElseThrow(() -> new IllegalStateException(String.format(
                        "The first heading of %s must be a level one heading (ie one #) for the title", name)));
    }

    public static MdFile fromString(String content) {
        return new MdFile(
                "in memory md file",
                StreamEx.of(content.lines())
                        .flatMap(line -> SUBHEADING_PATTERN.matcher(line).results())
                        .map(matchResult ->
                                new Heading(matchResult.group(1).length(), new HeadingText(matchResult.group(2))))
                        .toList());
    }

    public List<String> headingsAsStrings() {
        return headings.stream().map(Heading::text).map(HeadingText::text).toList();
    }

    public String title() {
        return title;
    }

    public Stream<Heading> headingsAtLevel(int level) {
        return headings.stream().filter(heading -> heading.level() == level);
    }

    public Stream<Heading> headingsUpToLevel(int level) {
        return headings.stream().filter(heading -> heading.level() <= level);
    }

    public static MdFile fromPath(Path mdFile) {
        try {
            return fromString(Files.readString(mdFile));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
