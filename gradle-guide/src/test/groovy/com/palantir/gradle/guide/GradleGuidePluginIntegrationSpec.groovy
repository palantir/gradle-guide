/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.guide

import nebula.test.IntegrationSpec

class GradleGuidePluginIntegrationSpec extends IntegrationSpec {
    def setup() {
        buildFile << """
            apply plugin: 'java-gradle-plugin'
            apply plugin: 'com.palantir.gradle-guide'
            
            repositories {
                mavenCentral()
                mavenLocal()
            }
        """.stripIndent(true)
    }

    def 'registers errorprones correctly'() {
        // language=Java
        writeJavaSourceFile('''
            import org.gradle.api.Project;
            import org.gradle.api.Task;

            final class Bad {
                public void apply(Project project) {
                    project.getTasks().create("bad", Task.class);
                }
            }
        '''.stripIndent(true))

        when:
        def stderr = runTasksWithFailure('compileJava').standardError

        then:
        stderr.contains 'error: [RegisterInsteadOfCreate]'
    }

}
