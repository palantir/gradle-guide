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
package com.palantir.gradle.guide.errorprone;

import com.google.errorprone.CompilationTestHelper;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

class NonAbstractGradleTypeTest {
    private final CompilationTestHelper compilationTestHelper =
            CompilationTestHelper.newInstance(NonAbstractGradleType.class, getClass());

    @Test
    void non_abstract_task_should_fail() {
        test(
                """
                import org.gradle.api.DefaultTask;

                // BUG: Diagnostic contains: abstract class
                class Test extends DefaultTask {}
                """);
    }

    @Test
    void non_abstract_extension_should_fail() {
        test(
                """
                // BUG: Diagnostic contains: abstract class
                class FooExtension {}
                """);
    }

    @Test
    void abstract_task_is_fine() {
        test(
                """
                import org.gradle.api.DefaultTask;

                abstract class Test extends DefaultTask {}
                """);
    }

    @Test
    void abstract_extension_is_fine() {
        test("""
                abstract class FooExtension {}
                """);
    }

    @Test
    void non_abstract_other_type_is_fine() {
        test("""
                class SomethingElse {}
                """);
    }

    private void test(@Language("Java") String source) {
        compilationTestHelper.addSourceLines("Test.java", source).doTest();
    }
}
