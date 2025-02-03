/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.guide

import nebula.test.IntegrationSpec

class GradleGuidePluginIntegrationSpec extends IntegrationSpec {
    def setup() {
        String version = new File('build/version').text

        buildFile << """
            apply plugin: 'java-gradle-plugin'
            apply plugin: 'com.palantir.gradle-guide'
            
            repositories {
                mavenCentral()
                mavenLocal()
            }
            
            dependencies {
                constraints {
                    errorprone "com.palantir.gradle.guide:gradle-guide-error-prone:${version}"
                }
            }
        """.stripIndent(true)
    }

    def 'errors on gradle java code that fails an errorprone check when enabled'() {
        // language=yml
        gradleGuideConfigYml = '''
            enabled:
              - RegisterInsteadOfCreate
        '''

        createInsteadOfRegisterExample()

        when:
        def stderr = runTasksWithFailure('compileJava').standardError
        println stderr

        then:
        stderr.contains 'error: [RegisterInsteadOfCreate]'
    }

    def 'no error on gradle java code that fails an errorprone check when not enabled'() {
        when:
        // language=yml
        gradleGuideConfigYml = '''
            enabled: []
        '''

        createInsteadOfRegisterExample()

        then:
        runTasksSuccessfully('compileJava')
    }

    def 'can tell you which checks would work but are not yet enabled'() {
        when:
        // language=yml
        gradleGuideConfigYml = '''
            enabled: []
        '''

        createInsteadOfRegisterExample()

        then:
        runTasksSuccessfully('enableGradleGuideChecksThatPass')

        and:
        // language=yml
        gradleGuideConfigYml().text.startsWith '''
            enabled:
              - RegisterInsteadOfCreate
        '''.stripIndent(true).strip()
    }

    private void setGradleGuideConfigYml(String contents) {
        def configYml = gradleGuideConfigYml()
        configYml.parentFile.mkdirs()
        configYml.createNewFile()
        configYml.text = contents.stripIndent(true).strip()
    }

    private File gradleGuideConfigYml() {
        new File(projectDir, '.palantir/gradle-guide.yml')
    }

    private void createInsteadOfRegisterExample() {
        // language=java
        writeJavaSourceFile '''
            import org.gradle.api.Project;
            import org.gradle.api.Task;

            final class Bad {
                public void apply(Project project) {
                    project.getTasks().create("bad", Task.class);
                }
            }
        '''.stripIndent(true)
    }
}
