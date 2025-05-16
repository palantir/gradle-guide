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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SuppressWarnings("MisformattedTestData")
class NonAbstractGradleTypeTest {
    private final CompilationTestHelper compilationTestHelper =
            CompilationTestHelper.newInstance(NonAbstractGradleType.class, getClass());

    @Nested
    class Tasks {
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
        void abstract_task_is_fine() {
            test(
                    """
                    import org.gradle.api.DefaultTask;

                    abstract class Test extends DefaultTask {}
                    """);
        }

        @Test
        void interface_task_should_be_fine() {
            test(
                    """
                    import org.gradle.api.Task;

                    interface TestTask extends Task {}
                    """);
        }

        @Test
        void abstract_task_with_non_abstract_property_getter_should_fail() {
            test(
                    """
                    import org.gradle.api.DefaultTask;
                    import org.gradle.api.provider.Property;
                    abstract class Test extends DefaultTask {
                        // BUG: Diagnostic contains: must be abstract
                        public Property<String> getFoo() { return null; }
                    }
                    """);
        }

        @Test
        void abstract_task_with_property_getter_not_starting_with_get_should_fail() {
            test(
                    """
                    import org.gradle.api.DefaultTask;
                    import org.gradle.api.provider.Property;
                    abstract class Test extends DefaultTask {
                        // BUG: Diagnostic contains: must be named starting with 'get'
                        public abstract Property<String> name();
                    }
                    """);
        }

        @Test
        void abstract_task_with_abstract_property_getter_is_fine() {
            test(
                    """
                    import org.gradle.api.DefaultTask;
                    import org.gradle.api.provider.Property;
                    abstract class Test extends DefaultTask {
                        public abstract Property<String> getFoo();
                    }
                    """);
        }

        @Test
        void abstract_task_with_non_property_getter_is_fine() {
            test(
                    """
                    import org.gradle.api.DefaultTask;
                    abstract class Test extends DefaultTask {
                        public String getSomething() { return "hello"; }
                    }
                    """);
        }

        @Test
        void abstract_task_with_property_getter_with_parameter_is_fine() {
            test(
                    """
                    import org.gradle.api.DefaultTask;
                    import org.gradle.api.provider.Property;
                    abstract class Test extends DefaultTask {
                        public Property<String> getFoo(String ignored) { return null; }
                    }
                    """);
        }
    }

    @Nested
    class Extensions {

        // language=Java
        private String pluginCode =
                """
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

        @Test
        void extension_with_non_abstract_property_getter_should_fail() {
            compilationTestHelper
                    .addSourceLines(
                            "FooExtension.java",
                            "import org.gradle.api.provider.Property;",
                            "abstract class FooExtension {",
                            "    public Property<String> getFoo() { return null; }",
                            "}")
                    .addSourceLines(
                            "FooPlugin.java",
                            """
                            import org.gradle.api.Plugin;
                            import org.gradle.api.Project;
                            public class FooPlugin implements Plugin<Project> {
                                @Override
                                public void apply(Project project) {
                                    // BUG: Diagnostic contains: managed property getter methods must be abstract
                                    project.getExtensions().create("foo", FooExtension.class);
                                }
                            }
                            """)
                    .doTest();
        }

        @Test
        void extension_with_property_getter_not_starting_with_get_should_fail() {
            compilationTestHelper
                    .addSourceLines(
                            "FooExtension.java",
                            "import org.gradle.api.provider.Property;",
                            "abstract class FooExtension {",
                            "    public abstract Property<String> name();",
                            "}")
                    .addSourceLines(
                            "FooPlugin.java",
                            """
                            import org.gradle.api.Plugin;
                            import org.gradle.api.Project;
                            public class FooPlugin implements Plugin<Project> {
                                @Override
                                public void apply(Project project) {
                                    // BUG: Diagnostic contains: methods must be named starting with 'get'
                                    project.getExtensions().create("foo", FooExtension.class);
                                }
                            }
                            """)
                    .doTest();
        }

        @Test
        void extension_with_abstract_property_getter_is_fine() {
            compilationTestHelper
                    .addSourceLines(
                            "FooExtension.java",
                            "import org.gradle.api.provider.Property;",
                            "abstract class FooExtension {",
                            "    public abstract Property<String> getFoo();",
                            "}")
                    .addSourceLines(
                            "FooPlugin.java",
                            """
                            import org.gradle.api.Plugin;
                            import org.gradle.api.Project;
                            public class FooPlugin implements Plugin<Project> {
                                @Override
                                public void apply(Project project) {
                                    project.getExtensions().create("foo", FooExtension.class);
                                }
                            }
                            """)
                    .expectNoDiagnostics()
                    .doTest();
        }

        @Test
        void extension_with_non_property_getter_is_fine() {
            compilationTestHelper
                    .addSourceLines(
                            "FooExtension.java",
                            "abstract class FooExtension {",
                            "    public String getSomething() { return \"hello\"; }",
                            "}")
                    .addSourceLines(
                            "FooPlugin.java",
                            """
                            import org.gradle.api.Plugin;
                            import org.gradle.api.Project;
                            public class FooPlugin implements Plugin<Project> {
                                @Override
                                public void apply(Project project) {
                                    project.getExtensions().create("foo", FooExtension.class);
                                }
                            }
                            """)
                    .expectNoDiagnostics()
                    .doTest();
        }

        @Test
        void extension_with_property_getter_with_parameter_is_fine() {
            compilationTestHelper
                    .addSourceLines(
                            "FooExtension.java",
                            "import org.gradle.api.provider.Property;",
                            "abstract class FooExtension {",
                            "    public Property<String> getFoo(String ignored) { return null; }",
                            "}")
                    .addSourceLines(
                            "FooPlugin.java",
                            """
                            import org.gradle.api.Plugin;
                            import org.gradle.api.Project;
                            public class FooPlugin implements Plugin<Project> {
                                @Override
                                public void apply(Project project) {
                                    project.getExtensions().create("foo", FooExtension.class);
                                }
                            }
                            """)
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

    @Test
    void unrelated_abstract_class_with_property_getter_is_fine() {
        test(
                """
                abstract class NotATaskOrExtension {
                    public abstract String getFoo();
                }
                """);
    }

    private void test(@Language("Java") String source) {
        compilationTestHelper.addSourceLines("Test.java", source).doTest();
    }
}
