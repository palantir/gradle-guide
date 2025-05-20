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
class NonAbstractGradleTypeFieldsTest {
    private final CompilationTestHelper compilationTestHelper =
            CompilationTestHelper.newInstance(NonAbstractGradleTypeFields.class, getClass());

    @Nested
    class Tasks {
        @Test
        void provider_field_in_constructor_should_fail() {
            test(
                    """
                    import org.gradle.api.DefaultTask;
                    import org.gradle.api.provider.Property;
                    abstract class Test extends DefaultTask {
                        // BUG: Diagnostic contains: declare class fields directly on Tasks or Extensions
                        private final Property<String> foo;
                        public Test() {
                            this.foo = getProject().getObjects().property(String.class);
                        }
                    }
                    """);
        }

        @Test
        void named_domain_container_field_in_constructor_should_fail() {
            test(
                    """
                    import org.gradle.api.DefaultTask;
                    import org.gradle.api.NamedDomainObjectContainer;
                    abstract class Test extends DefaultTask {
                        // BUG: Diagnostic contains: declare class fields directly on Tasks or Extensions
                        private final NamedDomainObjectContainer<String> foo;
                        public Test() {
                            this.foo = getProject().container(String.class);
                        }
                    }
                    """);
        }

        @Test
        void file_collection_field_in_constructor_should_fail() {
            test(
                    """
                    import org.gradle.api.DefaultTask;
                    import org.gradle.api.file.FileCollection;
                    abstract class Test extends DefaultTask {
                        // BUG: Diagnostic contains: declare class fields directly on Tasks or Extensions
                        private final FileCollection foo;
                        public Test() {
                            this.foo = getProject().files("somefile.txt");
                        }
                    }
                    """);
        }

        @Test
        void provider_field_directly_should_fail() {
            test(
                    """
                    import org.gradle.api.DefaultTask;
                    import org.gradle.api.provider.Property;
                    abstract class Test extends DefaultTask {
                        // BUG: Diagnostic contains: declare class fields directly on Tasks or Extensions
                        private final Property<String> foo = getProject().getObjects().property(String.class);
                        public Test() {}
                    }
                    """);
        }

        @Test
        void provider_internal_field_should_be_fine() {
            test(
                    """
                    import org.gradle.api.DefaultTask;
                    import org.gradle.api.provider.Provider;
                    abstract class Test extends DefaultTask {
                        public Test() {}

                        private Provider<String> foo() {
                            Provider<String> bar = getProject().getObjects().property(String.class);
                            return null;
                        }
                    }
                    """);
        }

        @Test
        void provider_local_variable_in_method_should_be_fine() {
            test(
                    """
                    import org.gradle.api.DefaultTask;
                    import org.gradle.api.provider.Property;
                    abstract class Test extends DefaultTask {
                        public Test() {
                            Property<String> bar = getProject().getObjects().property(String.class);
                        }
                    }
                    """);
        }

        @Test
        void provider_method_parameter_should_be_fine() {
            test(
                    """
                    import org.gradle.api.DefaultTask;
                    import org.gradle.api.provider.Property;
                    abstract class Test extends DefaultTask {
                        public Test(Property<String> bar) {}
                    }
                    """);
        }

        @Test
        void provider_field_on_inner_class_should_be_fine() {
            test(
                    """
                    import org.gradle.api.DefaultTask;
                    import org.gradle.api.provider.Property;
                    abstract class Test extends DefaultTask {
                        public Test() {}
                        class Inner {
                            private final Property<String> bar = null;
                        }
                    }
                    """);
        }

        @Test
        void unrelated_field_should_be_fine() {
            test(
                    """
                    import org.gradle.api.DefaultTask;
                    abstract class Test extends DefaultTask {
                        private final String bar = "hello";
                        public Test() {}
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
        void provider_field_in_constructor_should_fail() {
            compilationTestHelper
                    .addSourceLines(
                            "FooExtension.java",
                            "import org.gradle.api.provider.Property;",
                            "import org.gradle.api.Project;",
                            "abstract class FooExtension {",
                            "    private final Property<String> property;",
                            "    public FooExtension(Project project) {",
                            "        this.property = project.getObjects().property(String.class);",
                            "    }",
                            "}")
                    .addSourceLines(
                            "FooPlugin.java",
                            """
                            import org.gradle.api.Plugin;
                            import org.gradle.api.Project;
                            public class FooPlugin implements Plugin<Project> {
                                @Override
                                public void apply(Project project) {
                                    // BUG: Diagnostic contains: declare class fields directly on Tasks or Extensions
                                    project.getExtensions().create("foo", FooExtension.class);
                                }
                            }
                            """)
                    .doTest();
        }

        @Test
        void file_collection_field_in_constructor_should_fail() {
            compilationTestHelper
                    .addSourceLines(
                            "FooExtension.java",
                            "import org.gradle.api.file.FileCollection;",
                            "import org.gradle.api.Project;",
                            "abstract class FooExtension {",
                            "    private final FileCollection files;",
                            "    public FooExtension(Project project) {",
                            "        this.files = project.files(\"somefile.txt\");",
                            "    }",
                            "}")
                    .addSourceLines(
                            "FooPlugin.java",
                            """
                            import org.gradle.api.Plugin;
                            import org.gradle.api.Project;
                            public class FooPlugin implements Plugin<Project> {
                                @Override
                                public void apply(Project project) {
                                    // BUG: Diagnostic contains: declare class fields directly on Tasks or Extensions
                                    project.getExtensions().create("foo", FooExtension.class);
                                }
                            }
                            """)
                    .doTest();
        }

        @Test
        void named_domain_container_field_in_constructor_should_fail() {
            compilationTestHelper
                    .addSourceLines(
                            "FooExtension.java",
                            "import org.gradle.api.NamedDomainObjectContainer;",
                            "import org.gradle.api.Project;",
                            "abstract class FooExtension {",
                            "    private final NamedDomainObjectContainer<String> container;",
                            "    public FooExtension(Project project) {",
                            "        this.container = project.container(String.class);",
                            "    }",
                            "}")
                    .addSourceLines(
                            "FooPlugin.java",
                            """
                            import org.gradle.api.Plugin;
                            import org.gradle.api.Project;
                            public class FooPlugin implements Plugin<Project> {
                                @Override
                                public void apply(Project project) {
                                    // BUG: Diagnostic contains: declare class fields directly on Tasks or Extensions
                                    project.getExtensions().create("foo", FooExtension.class);
                                }
                            }
                            """)
                    .doTest();
        }

        @Test
        void provider_local_variable_in_method_should_be_fine() {
            compilationTestHelper
                    .addSourceLines(
                            "FooExtension.java",
                            "import org.gradle.api.provider.Property;",
                            "import org.gradle.api.Project;",
                            "abstract class FooExtension {",
                            "    public FooExtension(Project project) {",
                            "        Property<String> bar = project.getObjects().property(String.class);",
                            "    }",
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
                    .doTest();
        }

        @Test
        void provider_method_parameter_should_be_fine() {
            compilationTestHelper
                    .addSourceLines(
                            "FooExtension.java",
                            "import org.gradle.api.provider.Property;",
                            "import org.gradle.api.Project;",
                            "abstract class FooExtension {",
                            "    public FooExtension(Property<String> bar) {}",
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
                    .doTest();
        }

        @Test
        void provider_field_on_inner_class_should_be_fine() {
            compilationTestHelper
                    .addSourceLines(
                            "FooExtension.java",
                            "import org.gradle.api.provider.Property;",
                            "import org.gradle.api.Project;",
                            "abstract class FooExtension {",
                            "    public FooExtension(Project project) {}",
                            "    class Inner {",
                            "        private final Property<String> bar = null;",
                            "    }",
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
                    .doTest();
        }

        @Test
        void unrelated_field_should_be_fine() {
            compilationTestHelper
                    .addSourceLines(
                            "FooExtension.java",
                            "abstract class FooExtension {",
                            "    private final String bar = \"hello\";",
                            "    public FooExtension() {}",
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
                    .doTest();
        }
    }

    @Test
    void non_abstract_other_type_is_fine() {
        test(
                """
            import org.gradle.api.provider.Property;
            class NotATaskOrExtension {
                private final Property<String> foo = null;
                NotATaskOrExtension() {}
                private void bar() {
                    Property<String> local = null;
                }
                class Inner {
                    private final Property<String> inner = null;
                }
            }
            """);
    }

    private void test(@Language("Java") String source) {
        compilationTestHelper.addSourceLines("Test.java", source).doTest();
    }
}
