package com.palantir.gradle.guide.errorprone

import com.google.errorprone.CompilationTestHelper
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.junit.jupiter.api.Test

@CompileStatic(TypeCheckingMode.PASS)
class RegisterInsteadOfCreateTest {
    @Test
    void blah() {
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
}