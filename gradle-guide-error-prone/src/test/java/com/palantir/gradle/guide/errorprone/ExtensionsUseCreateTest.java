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

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.CompilationTestHelper;
import com.palantir.gradle.guide.helpers.RefactoringValidator;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExtensionsUseCreateTest {
    private final CompilationTestHelper compilationTestHelper =
            CompilationTestHelper.newInstance(ExtensionsUseCreate.class, getClass());

    @SuppressWarnings("for-rollout:UnusedMethod")
    private RefactoringValidator bestEffortRefactoringValidator() {
        return refactoringValidator("-XepOpt:GradleGuide:BestEffortMode");
    }

    private RefactoringValidator refactoringValidator(String... args) {
        return RefactoringValidator.of(ExtensionsUseCreate.class, getClass(), args);
    }

    @Nested
    class Diagnostics {
        @Test
        void flags_direct_new_instance_passed_to_add() {
            test("""
                import org.gradle.api.Project;
                class FooExtension { FooExtension(String a, String b) {} }
                class Test {
                    void foo(Project project) {
                        // BUG: Diagnostic contains: registered using `create`
                        project.getExtensions().add("foo", new FooExtension("a", "b"));
                    }
                }
                """);
        }

        @Test
        void flags_variable_with_new_instance_passed_to_add() {
            test("""
                import org.gradle.api.Project;
                class FooExtension { FooExtension(String a, String b) {} }
                class Test {
                    void foo(Project project) {
                        FooExtension ext = new FooExtension("a", "b");
                        // BUG: Diagnostic contains: registered using `create`
                        project.getExtensions().add("foo", ext);
                    }
                }
                """);
        }

        @Test
        void flags_direct_new_instance_passed_to_add_with_public_type() {
            test("""
                import org.gradle.api.Project;
                class FooExtension { FooExtension(String a, String b) {} }
                class Test {
                    void foo(Project project) {
                        // BUG: Diagnostic contains: registered using `create`
                        project.getExtensions().add(FooExtension.class, "foo", new FooExtension("a", "b"));
                    }
                }
                """);
        }

        @Test
        void flags_variable_new_instance_passed_to_add_with_public_type() {
            test("""
                import org.gradle.api.Project;
                class FooExtension { FooExtension(String a, String b) {} }
                class Test {
                    void foo(Project project) {
                        FooExtension ext = new FooExtension("a", "b");
                        // BUG: Diagnostic contains: registered using `create`
                        project.getExtensions().add(FooExtension.class, "foo", ext);
                    }
                }
                """);
        }

        @Test
        void allows_variable_with_method_call_passed_to_add() {
            test("""
                import org.gradle.api.Project;
                class FooExtension {}
                class Test {
                    static FooExtension loadExt() { return new FooExtension(); }
                    void foo(Project project) {
                        FooExtension ext = loadExt();
                        project.getExtensions().add("foo", ext);
                    }
                }
                """);
        }

        @Test
        void allows_direct_method_call_passed_to_add() {
            test("""
                import org.gradle.api.Project;
                class FooExtension {}
                class Test {
                    static FooExtension loadExt() { return new FooExtension(); }
                    void foo(Project project) {
                        project.getExtensions().add("foo", loadExt());
                    }
                }
                """);
        }

        @Test
        void allows_variable_with_method_call_passed_to_add_with_public_type() {
            test("""
                import org.gradle.api.Project;
                class FooExtension {}
                class Test {
                    static FooExtension loadExt() { return new FooExtension(); }
                    void foo(Project project) {
                        FooExtension ext = loadExt();
                        project.getExtensions().add(FooExtension.class, "foo", ext);
                    }
                }
                """);
        }

        @Test
        void allows_getByName() {
            test("""
                import org.gradle.api.Project;
                class Test {
                    void foo(Project project) {
                        project.getExtensions().add("foo", project.getRootProject().getExtensions()
                                                                                                .getByName("foo"));
                    }
                }
                """);
        }

        @Test
        void allows_findByType() {
            test("""
                import org.gradle.api.Project;
                class FooExtension {}
                class Test {
                    void foo(Project project) {
                        FooExtension ext = project.getExtensions().findByType(FooExtension.class);
                        project.getExtensions().add("foo", ext);
                    }
                }
                """);
        }

        private void test(@Language("Java") String source) {
            compilationTestHelper.addSourceLines("Test.java", source).doTest();
        }
    }

    @Nested
    class Refactoring {
        @Test
        void replaces_direct_new_instance_passed_to_add() {
            refactoringTest("""
                import org.gradle.api.Project;
                class Test {
                    void foo(Project project) {
                        project.getExtensions().add("foo", new FooExtension("a", "b"));
                    }

                    class FooExtension { FooExtension(String a, String b) {} }
                }
                """, """
                import org.gradle.api.Project;
                class Test {
                    void foo(Project project) {
                        project.getExtensions().create("foo", FooExtension.class, "a", "b");
                    }

                    class FooExtension { FooExtension(String a, String b) {} }
                }
                """);
        }

        @Test
        void replaces_variable_with_new_instance_passed_to_add() {
            refactoringTest("""
                import org.gradle.api.Project;
                class Test {
                    void foo(Project project) {
                        FooExtension ext = new FooExtension("a", "b");

                        project.getExtensions().add("foo", ext);
                    }

                    class FooExtension { FooExtension(String a, String b) {} }
                }
                """, """
                import org.gradle.api.Project;
                class Test {
                    void foo(Project project) {

                        project.getExtensions().create("foo", FooExtension.class, "a", "b");
                    }

                    class FooExtension { FooExtension(String a, String b) {} }
                }
                """);
        }

        @Test
        void replaces_direct_new_instance_passed_to_add_with_public_type() {
            refactoringTest("""
                import org.gradle.api.Project;
                class Test {
                    void foo(Project project) {
                        project.getExtensions().add(FooExtension.class, "foo", new FooExtension("a", "b"));
                    }
                    class FooExtension { FooExtension(String a, String b) {} }
                }
                """, """
                import org.gradle.api.Project;
                class Test {
                    void foo(Project project) {
                        project.getExtensions().create("foo", FooExtension.class, "a", "b");
                    }
                    class FooExtension { FooExtension(String a, String b) {} }
                }
                """);
        }

        @Test
        void replaces_variable_new_instance_passed_to_add_with_public_type() {
            refactoringTest("""
                import org.gradle.api.Project;
                class Test {
                    void foo(Project project) {
                        FooExtension ext = new FooExtension("a", "b");
                        project.getExtensions().add(FooExtension.class, "foo", ext);
                    }
                    class FooExtension { FooExtension(String a, String b) {} }
                }
                """, """
                import org.gradle.api.Project;
                class Test {
                    void foo(Project project) {

                        project.getExtensions().create("foo", FooExtension.class, "a", "b");
                    }
                    class FooExtension { FooExtension(String a, String b) {} }
                }
                """);
        }

        @Test
        void does_not_change_variable_with_method_call() {
            refactoringNoop("""
                import org.gradle.api.Project;
                class Test {
                    static FooExtension loadExt() { return new FooExtension(); }
                    void foo(Project project) {
                        FooExtension ext = loadExt();
                        project.getExtensions().add("foo", ext);
                    }

                    static class FooExtension {}
                }
                """);
        }

        @Test
        void does_not_change_direct_method_call() {
            refactoringNoop("""
                import org.gradle.api.Project;
                class Test {
                    static FooExtension loadExt() { return new FooExtension(); }
                    void foo(Project project) {
                        project.getExtensions().add("foo", loadExt());
                    }

                    static class FooExtension {}
                }
                """);
        }

        @Test
        void does_not_change_variable_with_method_call_passed_to_add_with_public_type() {
            refactoringNoop("""
                import org.gradle.api.Project;
                class Test {
                    static FooExtension loadExt() { return new FooExtension(); }
                    void foo(Project project) {
                        FooExtension ext = loadExt();
                        project.getExtensions().add(FooExtension.class, "foo", ext);
                    }

                    static class FooExtension {}
                }
                """);
        }

        @Test
        void does_not_change_getByName() {
            refactoringNoop("""
                import org.gradle.api.Project;
                class Test {
                    void foo(Project project) {
                        project.getExtensions().add("foo", project.getRootProject().getExtensions()
                                                                                                .getByName("foo"));
                    }
                }
                """);
        }

        @Test
        void does_not_change_findByType() {
            refactoringNoop("""
                import org.gradle.api.Project;
                class Test {
                    void foo(Project project) {
                        FooExtension ext = project.getExtensions().findByType(FooExtension.class);
                        project.getExtensions().add("foo", ext);
                    }

                    class FooExtension {}
                }
                """);
        }

        @SuppressWarnings("deprecation")
        private void refactoringTest(@Language("Java") String input, @Language("Java") String expected) {
            BugCheckerRefactoringTestHelper refactoringTestHelper =
                    BugCheckerRefactoringTestHelper.newInstance(ExtensionsUseCreate.class, getClass());

            refactoringTestHelper
                    .addInputLines("Test.java", input)
                    .addOutputLines("Test.java", expected)
                    .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
        }

        @SuppressWarnings("deprecation")
        private void refactoringNoop(@Language("Java") String code) {
            BugCheckerRefactoringTestHelper refactoringTestHelper =
                    BugCheckerRefactoringTestHelper.newInstance(ExtensionsUseCreate.class, getClass());

            refactoringTestHelper
                    .addInputLines("Test.java", code)
                    .expectUnchanged()
                    .doTest(BugCheckerRefactoringTestHelper.TestMode.TEXT_MATCH);
        }
    }
}
