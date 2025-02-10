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
