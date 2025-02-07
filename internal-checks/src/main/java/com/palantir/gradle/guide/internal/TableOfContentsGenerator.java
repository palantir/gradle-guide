/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 */
package com.palantir.gradle.guide.internal;

import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import one.util.streamex.StreamEx;
import org.apache.commons.lang3.StringUtils;

final class TableOfContentsGenerator {

    private static final String TOC_SOURCE_START = "<!-- TableOfContentsSource:";
    private static final String TOC_SOURCE_END = "-->";
    private static final String TOC_START = "<!-- TableOfContents: START -->";
    private static final String TOC_END = "<!-- TableOfContents: END -->";

    private static final Pattern SUBHEADING_PATTERN = Pattern.compile("^## (.+)$");

    public static String generate(String readmeContent, Path guideDir) {
        List<String> lines = readmeContent
                .lines()
                .dropWhile(line -> !line.contains(TOC_SOURCE_START))
                .skip(1)
                .takeWhile(line -> !line.contains(TOC_SOURCE_END))
                .toList();

        if (lines.isEmpty()) {
            throw new RuntimeException("No TableOfContentsSource found in README.md");
        }

        List<Path> mdFilesInContents = lines.stream()
                .map(line -> line.replace("* ", ""))
                .map(guideDir::resolve)
                .toList();

        List<Path> mdFilesThatDontExist =
                mdFilesInContents.stream().filter(Files::notExists).toList();

        if (!mdFilesThatDontExist.isEmpty()) {
            throw new RuntimeException(
                    "The following files in the table of contents do not exist: " + mdFilesThatDontExist);
        }

        Set<Path> mdFilesNotInTableOfContents = Sets.difference(allMdFiles(guideDir), new HashSet<>(mdFilesInContents));

        if (!mdFilesNotInTableOfContents.isEmpty()) {
            throw new RuntimeException(
                    "The following files are not in the table of contents: " + mdFilesNotInTableOfContents);
        }

        String toc = StreamEx.of(mdFilesInContents)
                .zipWith(integers())
                .mapKeyValue((mdFile, i) -> contentsSectionForMdFile(guideDir, mdFile, i))
                .joining("\n");

        int start = readmeContent.indexOf(TOC_START) + TOC_START.length();
        int end = readmeContent.indexOf(TOC_END);

        return readmeContent.substring(0, start) + "\n" + toc + "\n" + readmeContent.substring(end);
    }

    private static String contentsSectionForMdFile(Path guideDir, Path mdFile, Integer index) {
        String filename = mdFile.getFileName().toString();
        String title = StringUtils.capitalize(
                filename.substring(0, filename.length() - ".md".length()).replace('-', ' '));
        String top = String.format("%s. [%s](guide/%s)", index, title, guideDir.relativize(mdFile));

        try {
            List<String> mdFileContent = Files.readAllLines(mdFile);
            String subheadings = StreamEx.of(mdFileContent)
                    .flatMap(line -> SUBHEADING_PATTERN.matcher(line).results())
                    .zipWith(integers())
                    .mapKeyValue((matchResult, subIndex) -> {
                        String subheading = matchResult.group(1);
                        String subheadingLink = subheading
                                .toLowerCase(Locale.ROOT)
                                .replace(" ", "-")
                                .replaceAll("[/`<>]", "");
                        return String.format(
                                "    %d. [%s](guide/%s#%s)",
                                subIndex, subheading, guideDir.relativize(mdFile), subheadingLink);
                    })
                    .collect(Collectors.joining("\n"));

            return top + "\n" + subheadings;
        } catch (IOException e) {
            throw new RuntimeException("Failed to process file " + mdFile, e);
        }
    }

    private static IntStream integers() {
        return IntStream.iterate(1, i -> i + 1);
    }

    private static Set<Path> allMdFiles(Path guideDir) {
        try (Stream<Path> allMdFiles = Files.list(guideDir)) {
            return allMdFiles
                    .filter(path -> !path.toFile().isDirectory())
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private TableOfContentsGenerator() {}
}
