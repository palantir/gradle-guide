/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 */
package com.palantir.gradle.guide.internal

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path

import static org.assertj.core.api.Assertions.assertThat

@CompileStatic(TypeCheckingMode.PASS)
class TableOfContentsGeneratorTest {
    @Test
    void generates_a_good_table_of_contents(@TempDir Path guideDir) {
        guideDir.resolve("starting-stuff.md") << /* language=markdown */ '''
            # Starting Stuff
            
            ## Subheading 1
            
            ## Subheading with `code` elements/slashes <>
        '''.stripIndent(true)

        guideDir.resolve("more-stuff.md") << /* language=markdown */ '''
            # More Stuff
            
            ## Subheading 1
            
            ## Subheading 2
        '''.stripIndent(true)

        // language=markdown
        def readme = '''
            # Title
            
            <!-- TableOfContentsSource:
            * starting-stuff.md
            * more-stuff.md
            -->
            
            <!-- TableOfContents: START -->
            some other stuff that was here
            <!-- TableOfContents: END -->
            
            other stuff afterwards
        '''.stripIndent(true)

        String newReadme = TableOfContentsGenerator.generate(readme, guideDir)

        // language=markdown
        assertThat(newReadme).isEqualTo '''
            # Title
            
            <!-- TableOfContentsSource:
            * starting-stuff.md
            * more-stuff.md
            -->
            
            <!-- TableOfContents: START -->
            1. [Starting stuff](guide/starting-stuff.md)
                1. [Subheading 1](guide/starting-stuff.md#subheading-1)
                2. [Subheading with `code` elements/slashes <>](guide/starting-stuff.md#subheading-with-code-elementsslashes---)
            2. [More stuff](guide/more-stuff.md)
                1. [Subheading 1](guide/more-stuff.md#subheading-1)
                2. [Subheading 2](guide/more-stuff.md#subheading-2)
            <!-- TableOfContents: END -->
            
            other stuff afterwards
        '''.stripIndent(true)
    }
}