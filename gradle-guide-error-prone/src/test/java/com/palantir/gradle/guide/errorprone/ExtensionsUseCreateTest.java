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

public class ExtensionsUseCreateTest {
    private final CompilationTestHelper compilationTestHelper =
            CompilationTestHelper.newInstance(ExtensionsUseCreate.class, getClass());

    @Test
    void flags_direct_new_instance_passed_to_add() {
        test(
                """
                import org.gradle.api.Project;
                class FooExtension { FooExtension(String a, String b) {} }
                class Test {
                    void foo(Project project) {
                        // BUG: Diagnostic contains: Register extensions with `create`
                        project.getExtensions().add("foo", new FooExtension("a", "b"));
                    }
                }
                """);
    }

    @Test
    void flags_variable_with_new_instance_passed_to_add() {
        test(
                """
                import org.gradle.api.Project;
                class FooExtension { FooExtension(String a, String b) {} }
                class Test {
                    void foo(Project project) {
                        FooExtension ext = new FooExtension("a", "b");
                        // BUG: Diagnostic contains: Register extensions with `create`
                        project.getExtensions().add("foo", ext);
                    }
                }
                """);
    }

    @Test
    void allows_variable_with_method_call_passed_to_add() {
        test(
                """
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
        test(
                """
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
    void allows_getByName() {
        test(
                """
                import org.gradle.api.Project;
                class Test {
                    void foo(Project project) {
                        project.getExtensions().add("foo", project.getRootProject().getExtensions().getByName("foo"));
                    }
                }
                """);
    }

    @Test
    void allows_findByType() {
        test(
                """
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
