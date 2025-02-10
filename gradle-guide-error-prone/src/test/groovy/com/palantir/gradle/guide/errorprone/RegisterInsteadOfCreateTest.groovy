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
package com.palantir.gradle.guide.errorprone

import com.google.errorprone.CompilationTestHelper
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.junit.jupiter.api.Test

@CompileStatic(TypeCheckingMode.PASS)
class RegisterInsteadOfCreateTest {
    @Test
    void matches_tasks_create() {
        CompilationTestHelper compilationTestHelper = CompilationTestHelper.newInstance(RegisterInsteadOfCreate.class, getClass())

        compilationTestHelper.addSourceLines 'Test.java', /* language=java */ '''
            import org.gradle.api.tasks.TaskContainer;

            class Test {
                static void test(TaskContainer tasks) {
                    // BUG: Diagnostic contains: Don't do this yo
                    tasks.create("lol");
                }
            }
        '''.stripIndent(true)

        compilationTestHelper.doTest()
    }

    @Test
    void matches_configurations_create() {
        CompilationTestHelper compilationTestHelper = CompilationTestHelper.newInstance(RegisterInsteadOfCreate.class, getClass())

        compilationTestHelper.addSourceLines 'Test.java', /* language=java */ '''
            import org.gradle.api.artifacts.ConfigurationContainer;

            class Test {
                static void test(ConfigurationContainer configurations) {
                    // BUG: Diagnostic contains: Don't do this yo
                    configurations.create("lol");
                }
            }
        '''.stripIndent(true)

        compilationTestHelper.doTest()
    }
}