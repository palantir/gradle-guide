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
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.gradle.testing.project.SubProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache("test depends on mavenLocal and errorprone configuration adjustments")
class GradleGuidePluginIntegrationTest {

    @BeforeEach
    void setup(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("com.palantir.gradle-guide");
        rootProject.buildGradle().append("""
            allprojects {
                repositories {
                    mavenCentral()
                    mavenLocal()
                }

                pluginManager.withPlugin('com.palantir.suppressible-error-prone') {
                    suppressibleErrorProne {
                       // Our test source files are placed under `build/nebulatest`, which is ignored by default
                        configureEachErrorProneOptions {
                            it.excludedPaths.unset()
                        }
                    }

                    dependencies {
                        constraints {
                            errorprone "com.palantir.gradle.guide:gradle-guide-error-prone:%s"
                        }

                        errorprone 'com.google.errorprone:error_prone_core:2.36.0'
                    }
                }
            }
            """, System.getProperty("gradleGuideErrorProneVersion"));
    }

    @Test
    void registers_errorprones_correctly_in_subproject_that_uses_gradle_api(
            GradleInvoker gradle, SubProject gradleApi) {
        gradleApi.buildGradle().plugins().add("java");
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

        InvocationResult result = gradle.withArgs(":gradleApi:compileJava").buildsWithFailure();

        assertThat(result)
                .output()
                .as("gradle-guide errorprones should be registered on the subproject")
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

        InvocationResult result =
                gradle.withArgs(":javaGradlePlugin:compileJava").buildsWithFailure();

        assertThat(result)
                .output()
                .as("gradle-guide errorprones should be registered on the subproject")
                .contains("error: [ConfigurationAvoidanceRegistration]");
    }
}
