/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 */
package com.palantir.gradle.guide.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import one.util.streamex.StreamEx;
import org.apache.commons.lang3.StringUtils;

final class TableOfContentsGenerator {

    private static final String TOC_START = "<!-- TableOfContents: START -->";
    private static final String TOC_END = "<!-- TableOfContents: END -->";

    private static final Pattern SUBHEADING_PATTERN = Pattern.compile("^## (.+)$");

    public static String generate(String readmeContent, Path guideDir) {
        try (Stream<Path> mdFiles = Files.list(guideDir)) {
            String toc = StreamEx.of(mdFiles)
                    .filter(path -> !path.toFile().isDirectory())
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .zipWith(integers())
                    .mapKeyValue((path, i) -> mdFile(guideDir, path, i))
                    .joining("\n");

            int start = readmeContent.indexOf(TOC_START) + TOC_START.length();
            int end = readmeContent.indexOf(TOC_END);

            return readmeContent.substring(0, start) + "\n" + toc + "\n" + readmeContent.substring(end);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String mdFile(Path guideDir, Path path, Integer index) {
        String filename = path.getFileName().toString();
        String title = StringUtils.capitalize(filename.substring("01-".length(), filename.length() - ".md".length())
                .replace('-', ' '));
        String top = String.format("%s. [%s](guide/%s)", index, title, guideDir.relativize(path));

        try {
            List<String> mdFileContent = Files.readAllLines(path);
            String subheadings = StreamEx.of(mdFileContent)
                    .flatMap(line -> SUBHEADING_PATTERN.matcher(line).results())
                    .zipWith(integers())
                    .mapKeyValue((matchResult, subIndex) -> {
                        String subheading = matchResult.group(1);
                        String subheadingLink =
                                subheading.toLowerCase().replace(' ', '-').replaceAll("[/`]", "");
                        return String.format(
                                "    %d. [%s](guide/%s#%s)",
                                subIndex, subheading, guideDir.relativize(path), subheadingLink);
                    })
                    .collect(Collectors.joining("\n"));

            return top + "\n" + subheadings;
        } catch (IOException e) {
            throw new RuntimeException("Failed to process file " + path, e);
        }
    }

    private static IntStream integers() {
        return IntStream.iterate(1, i -> i + 1);
    }

    private TableOfContentsGenerator() {}
}
