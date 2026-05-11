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

package com.palantir.gradle.guide;

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.gradle.testing.project.SubProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache
class GradleGuidePluginIntegrationTest {

    @BeforeEach
    void before(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("com.palantir.gradle-guide").add("com.palantir.baseline-java-versions");

        rootProject.buildGradle().append("""
            javaVersions {
                javaCompiler = 25
                libraryTarget = 17
            }

            allprojects {
                repositories {
                    mavenCentral()
                    mavenLocal()
                }

                apply plugin: 'java'

                pluginManager.withPlugin('com.palantir.suppressible-error-prone') {
                    suppressibleErrorProne {
                        configureEachErrorProneOptions {
                            it.excludedPaths.unset()
                        }
                    }

                    dependencies {
                        constraints {
                            errorprone "com.palantir.gradle.guide:gradle-guide-error-prone:${System.getProperty('gradleGuideErrorProneVersion')}"
                        }

                        errorprone 'com.google.errorprone:error_prone_core:2.49.0'
                    }
                }
            }
            """);
    }

    @Test
    void registers_errorprones_correctly_in_subproject_that_uses_gradle_api(
            GradleInvoker gradle, SubProject gradleApi) {
        gradleApi.buildGradle().append("""
            dependencies {
                compileOnly gradleApi()
            }
            """);

        gradleApi.mainSourceSet().java().writeClass("""
            import org.gradle.api.Project;

            final class Bad {
                public void apply(Project project) {
                    project.getTasks().create("bad");
                }
            }
            """);

        assertThat(gradle.withArgs(":gradleApi:compileJava").buildsWithFailure())
                .output()
                .contains("error: [ConfigurationAvoidanceRegistration]");
    }

    @Test
    void registers_errorprones_correctly_in_subproject_that_uses_java_gradle_plugin(
            GradleInvoker gradle, SubProject javaGradlePlugin) {
        javaGradlePlugin.buildGradle().plugins().add("java-gradle-plugin");

        javaGradlePlugin.mainSourceSet().java().writeClass("""
            import org.gradle.api.Project;

            final class Bad {
                public void apply(Project project) {
                    project.getTasks().create("bad");
                }
            }
            """);

        assertThat(gradle.withArgs(":javaGradlePlugin:compileJava").buildsWithFailure())
                .output()
                .contains("error: [ConfigurationAvoidanceRegistration]");
    }
}
