/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.guide

import nebula.test.IntegrationSpec

class GradleGuidePluginIntegrationSpec extends IntegrationSpec {
    def setup() {
        // language=Gradle
        buildFile << '''
            apply plugin: 'java-gradle-plugin'
            apply plugin: 'com.palantir.gradle-guide'
            
            repositories {
                mavenCentral()
                mavenLocal()
            }
            
            dependencies {
                constraints {
                    errorprone "com.palantir.gradle.guide:gradle-guide-error-prone:${System.getProperty('gradleGuideErrorProneVersion')}"
                }
                
                errorprone 'com.google.errorprone:error_prone_core:2.36.0'
            }
            
            suppressibleErrorProne {
                // Our test source files are placed under `build/nebulatest`, which is ignored by default
                configureEachErrorProneOptions {
                    it.excludedPaths.unset()
                }
            }
        '''.stripIndent(true)
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
