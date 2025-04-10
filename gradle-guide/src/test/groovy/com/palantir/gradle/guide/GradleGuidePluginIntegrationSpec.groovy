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
    File gradleApiSubproject
    File javaWithoutGradleApiSubproject

    def setup() {
        // language=Gradle
        buildFile << '''
            apply plugin: 'com.palantir.gradle-guide'
            
            allprojects {
                repositories {
                    mavenCentral()
                    mavenLocal()
                }
                
                apply plugin: 'java'
             
                pluginManager.withPlugin('com.palantir.suppressible-error-prone') {
                    suppressibleErrorProne {
                       // Our test source files are placed under `build/nebulatest`, which is ignored by default
                        configureEachErrorProneOptions {
                            it.excludedPaths.unset()
                        }   
                    }
                    
                    dependencies {
                        constraints {
                            errorprone "com.palantir.gradle.guide:gradle-guide-error-prone:${System.getProperty('gradleGuideErrorProneVersion')}"
                        }
                        
                        errorprone 'com.google.errorprone:error_prone_core:2.36.0'
                    }
                }
            }
        '''.stripIndent(true)

        gradleApiSubproject = addSubproject('gradleApi')
        def gradleApiSubprojectBuildFile = file('build.gradle', gradleApiSubproject)

        // language=Gradle
        gradleApiSubprojectBuildFile << '''
            dependencies {
                compileOnly gradleApi()
            }
        '''.stripIndent(true)

        javaWithoutGradleApiSubproject = addSubproject('javaWithoutGradleApi')
    }

    def 'registers errorprones correctly in subproject that uses gradleApi'() {
        // language=Java
        writeJavaSourceFile('''
            import org.gradle.api.Project;

            final class Bad {
                public void apply(Project project) {
                    project.getTasks().create("bad");
                }
            }
        '''.stripIndent(true), gradleApiSubproject)

        when:
        def stderr = runTasksWithFailure(':gradleApi:compileJava').standardError

        then:
        stderr.contains 'error: [ConfigurationAvoidanceRegistration]'
    }

    def 'does not enable plugin in java subproject that doesnt use gradleApi'() {
        when:
        def stderr = runTasksSuccessfully(':javaWithoutGradleApi:compileJava').standardError

        then:
        !stderr.contains('error: [ConfigurationAvoidanceRegistration]')
    }

}
