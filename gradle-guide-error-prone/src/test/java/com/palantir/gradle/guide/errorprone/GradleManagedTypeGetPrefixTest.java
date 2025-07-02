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

@SuppressWarnings("MisformattedTestData")
class Test {
    private final CompilationTestHelper compilationTestHelper =
            CompilationTestHelper.newInstance(GradleManagedTypeGetPrefix.class, getClass());

    @Nested
    class Tasks {
        @Test
        void abstract_method_without_get_prefix_should_fail() {
            test(
                    """
                    import org.gradle.api.DefaultTask;
                    import org.gradle.api.provider.Property;
                    abstract class Test extends DefaultTask {
                        // BUG: Diagnostic contains: should start with 'get'
                        public abstract Property<String> foo();
                    }
                    """);
        }

        @Test
        void named_domain_abstract_method_without_get_prefix_should_fail() {
            test(
                    """
                    import org.gradle.api.DefaultTask;
                    import org.gradle.api.NamedDomainObjectContainer;
                    abstract class Test extends DefaultTask {
                        // BUG: Diagnostic contains: should start with 'get'
                        public abstract NamedDomainObjectContainer<String> foo();
                    }
                    """);
        }

        @Test
        void file_collection_abstract_method_without_get_prefix_should_fail() {
            test(
                    """
                    import org.gradle.api.DefaultTask;
                    import org.gradle.api.file.FileCollection;
                    abstract class Test extends DefaultTask {
                        // BUG: Diagnostic contains: should start with 'get'
                        public abstract FileCollection foo();
                    }
                    """);
        }

        @Test
        void abstract_method_with_get_prefix_should_be_fine() {
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
        void non_abstract_method_should_be_fine() {
            test(
                    """
                    import org.gradle.api.DefaultTask;
                    import org.gradle.api.provider.Property;
                    abstract class Test extends DefaultTask {
                        public Property<String> foo() {
                            return getProject().getObjects().property(String.class);
                        }
                    }
                    """);
        }

        @Test
        void method_returning_non_managed_type_should_be_fine() {
            test(
                    """
                    import org.gradle.api.DefaultTask;
                    abstract class Test extends DefaultTask {
                        public abstract String foo();
                    }
                    """);
        }
    }

    @Nested
    class Extensions {
        @Test
        void abstract_method_without_get_prefix_should_fail() {
            testExtension(
                    """
                    import org.gradle.api.provider.Property;
                    abstract class FooExtension {
                        public abstract Property<String> foo();
                    }
                    """,
                    PLUGIN_CODE_BUG);
        }

        @Test
        void named_domain_abstract_method_without_get_prefix_should_fail() {
            testExtension(
                    """
                    import org.gradle.api.DefaultTask;
                    import org.gradle.api.NamedDomainObjectContainer;
                    abstract class FooExtension {
                        public abstract NamedDomainObjectContainer<String> foo();
                    }
                    """,
                    PLUGIN_CODE_BUG);
        }

        @Test
        void file_collection_abstract_method_without_get_prefix_should_fail() {
            testExtension(
                    """
                    import org.gradle.api.DefaultTask;
                    import org.gradle.api.file.FileCollection;
                    abstract class FooExtension {
                        public abstract FileCollection foo();
                    }
                    """,
                    PLUGIN_CODE_BUG);
        }

        @Test
        void abstract_method_with_get_prefix_should_be_fine() {
            testExtension(
                    """
                    import org.gradle.api.provider.Property;
                    abstract class FooExtension {
                        public abstract Property<String> getFoo();
                    }
                    """,
                    PLUGIN_CODE_NO_BUG);
        }

        @Test
        void non_abstract_method_should_be_fine() {
            testExtension(
                    """
                    import org.gradle.api.provider.Property;
                    abstract class FooExtension {
                        public Property<String> foo() {
                            return null;
                        }
                    }
                    """,
                    PLUGIN_CODE_NO_BUG);
        }

        @Test
        void method_returning_non_managed_type_should_be_fine() {
            testExtension(
                    """
                    abstract class FooExtension {
                        public abstract String foo();
                    }
                    """,
                    PLUGIN_CODE_NO_BUG);
        }

        @Language("Java")
        private static final String PLUGIN_CODE_BUG =
                """
        import org.gradle.api.Plugin;
        import org.gradle.api.Project;
        public class FooPlugin implements Plugin<Project> {
            @Override
            public void apply(Project project) {
                // BUG: Diagnostic contains: should start with 'get'
                project.getExtensions().create("foo", FooExtension.class);
            }
        }
        """;

        @Language("Java")
        private static final String PLUGIN_CODE_NO_BUG =
                """
        import org.gradle.api.Plugin;
        import org.gradle.api.Project;
        public class FooPlugin implements Plugin<Project> {
            @Override
            public void apply(Project project) {
                project.getExtensions().create("foo", FooExtension.class);
            }
        }
        """;
    }

    @Test
    void non_abstract_other_type_is_fine() {
        test(
                """
            import org.gradle.api.provider.Property;
            abstract class NotATaskOrExtension {
                private final Property<String> foo = null;
                NotATaskOrExtension() {}
                private void bar() {
                    Property<String> local = null;
                }
                class Inner {
                    private final Property<String> inner = null;
                }
                public abstract Property<String> foo();
            }
            """);
    }

    private void test(@Language("Java") String source) {
        compilationTestHelper.addSourceLines("Test.java", source).doTest();
    }

    private void testExtension(@Language("Java") String extensionSource, @Language("Java") String pluginCode) {
        compilationTestHelper
                .addSourceLines("FooExtension.java", extensionSource)
                .addSourceLines("FooPlugin.java", pluginCode)
                .doTest();
    }
}
