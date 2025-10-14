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
import com.palantir.gradle.guide.helpers.RefactoringValidator;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SuppressWarnings("MisformattedTestData")
class NonAbstractGradleTypeTest {
    private final CompilationTestHelper compilationTestHelper =
            CompilationTestHelper.newInstance(NonAbstractGradleType.class, getClass());

    @Nested
    class Tasks {
        @Test
        void non_abstract_task_should_autofix() {
            testFix("Test", """
                import org.gradle.api.DefaultTask;

                class Test extends DefaultTask {}
                """, """
                import org.gradle.api.DefaultTask;

                abstract class Test extends DefaultTask {}
                """);
        }

        @Test
        void non_abstract_task_subtype_should_autofix() {
            testFix("Test", """
                import org.gradle.api.DefaultTask;

                class Foo {
                    abstract class Parent extends DefaultTask {}
                    class Child extends Parent {}
                }
                """, """
                import org.gradle.api.DefaultTask;

                class Foo {
                    abstract class Parent extends DefaultTask {}
                    abstract class Child extends Parent {}
                }
                """);
        }

        @Test
        void abstract_task_is_fine() {
            test("""
                import org.gradle.api.DefaultTask;

                abstract class Test extends DefaultTask {}
                """);
        }

        @Test
        void interface_task_should_be_fine() {
            test("""
                import org.gradle.api.Task;

                interface TestTask extends Task {}
                """);
        }
    }

    @Nested
    class Extensions {

        // language=Java
        private String pluginCode = """
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            public class FooPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    // BUG: Diagnostic contains: abstract class
                    project.getExtensions().create("foo", FooExtension.class);

                    Class<FooExtension> fooExtensionClass = FooExtension.class;
                    // BUG: Diagnostic contains: abstract class
                    project.getExtensions().create("foo", fooExtensionClass);
                }
            }
            """;

        @Test
        void non_abstract_extension_should_fail() {
            compilationTestHelper
                    .addSourceLines(
                            "FooExtension.java",
                            // language=Java
                            "class FooExtension {}")
                    .addSourceLines("FooPlugin.java", pluginCode)
                    .doTest();
        }

        @Test
        void abstract_extension_is_fine() {
            compilationTestHelper
                    .addSourceLines(
                            "FooExtension.java",
                            // language=Java
                            "abstract class FooExtension {}")
                    .addSourceLines("FooPlugin.java", pluginCode)
                    .expectNoDiagnostics()
                    .doTest();
        }

        @Test
        void interface_extension_is_fine() {
            compilationTestHelper
                    .addSourceLines(
                            "FooExtension.java",
                            // language=Java
                            "interface FooExtension {}")
                    .addSourceLines("FooPlugin.java", pluginCode)
                    .expectNoDiagnostics()
                    .doTest();
        }
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

    private void testFix(String filename, @Language("Java") String before, @Language("Java") String after) {
        RefactoringValidator.of(NonAbstractGradleType.class, getClass())
                .addInputLines(filename, before)
                .addOutputLines(filename, after)
                .doTest();
    }
}
