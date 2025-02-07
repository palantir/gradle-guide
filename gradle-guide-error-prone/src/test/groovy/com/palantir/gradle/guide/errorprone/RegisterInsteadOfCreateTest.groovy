/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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