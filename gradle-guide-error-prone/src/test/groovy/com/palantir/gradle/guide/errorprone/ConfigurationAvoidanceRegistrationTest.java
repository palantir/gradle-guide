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
import org.junit.jupiter.api.Test;

class ConfigurationAvoidanceRegistrationTest {
    private final CompilationTestHelper compilationTestHelper =
            CompilationTestHelper.newInstance(ConfigurationAvoidanceRegistration.class, getClass());
    private final RefactoringValidator refactoringValidator =
            RefactoringValidator.of(ConfigurationAvoidanceRegistration.class, getClass());

    @Test
    void matches_tasks_create() {
        compilationTestHelper.addSourceLines(
                "Test.java",
                // language=java
                """
            import groovy.lang.Closure;
            import java.util.Map;
            import org.gradle.api.Task;
            import org.gradle.api.tasks.TaskContainer;

            class Test {
                static void test(TaskContainer tasks) {
                    // BUG: Diagnostic contains: use `.register`
                    tasks.create(Map.of("name", "lol", "type", Task.class));
                    // BUG: Diagnostic contains: use `.register`
                    tasks.create(Map.of("name", "lol", "type", Task.class), Closure.IDENTITY);
                    // BUG: Diagnostic contains: use `.register`
                    tasks.create("lol", Closure.IDENTITY);
                    // BUG: Diagnostic contains: use `.register`
                    tasks.create("lol");
                    // BUG: Diagnostic contains: use `.register`
                    tasks.create("lol", Task.class);
                    // BUG: Diagnostic contains: use `.register`
                    tasks.create("lol", Task.class, new Object());
                    // BUG: Diagnostic contains: use `.register`
                    tasks.create("lol", Task.class, task -> {});
                }
            }
            """);

        compilationTestHelper.doTest();
    }

    @Test
    void matches_configurations_create() {
        compilationTestHelper.addSourceLines(
                "Test.java",
                // language=java
                """
            import groovy.lang.Closure;
            import org.gradle.api.artifacts.ConfigurationContainer;

            class Test {
                static void test(ConfigurationContainer configurations) {
                    // BUG: Diagnostic contains: use `.register`
                    configurations.create("lol");
                    // BUG: Diagnostic contains: use `.register`
                    configurations.create("lol", Closure.IDENTITY);
                    // BUG: Diagnostic contains: use `.register`
                    configurations.create("lol", conf -> {});
                }
            }
            """);

        compilationTestHelper.doTest();
    }

    @Test
    void matches_sourcesets_create() {
        compilationTestHelper.addSourceLines(
                "Test.java",
                // language=java
                """
            import groovy.lang.Closure;
            import org.gradle.api.tasks.SourceSetContainer;

            class Test {
                static void test(SourceSetContainer sourceSets) {
                    // BUG: Diagnostic contains: use `.register`
                    sourceSets.create("lol");
                    // BUG: Diagnostic contains: use `.register`
                    sourceSets.create("lol", Closure.IDENTITY);
                    // BUG: Diagnostic contains: use `.register`
                    sourceSets.create("lol", conf -> {});
                }
            }
            """);

        compilationTestHelper.doTest();
    }

    @Test
    void refactors_usages_of_create_where_the_return_value_is_unused_to_register() {
        refactoringValidator
                .addInputLines(
                        "Test.java",
                        // language=java
                        """
            import groovy.lang.Closure;
            import java.util.Map;
            import org.gradle.api.Task;
            import org.gradle.api.tasks.TaskContainer;

            class Test {
                static void unused_return_value(TaskContainer tasks) {
                    tasks.create("lol");
                    tasks.create("lol", Task.class);
                    tasks.create("lol", Task.class, new Object());
                    tasks.create("lol", Task.class, task -> {});
                }

                static Task used_return_value(TaskContainer tasks) {
                    // BUG: Diagnostic contains: use `.register`
                    Task task = tasks.create("lol");
                    // BUG: Diagnostic contains: use `.register`
                    System.out.println(tasks.create("lol", Task.class, t -> {}));
                    // BUG: Diagnostic contains: use `.register`
                    return tasks.create("lol", Task.class);
                }

                static void methods_with_no_directly_equivalent_register(TaskContainer tasks) {
                    // BUG: Diagnostic contains: use `.register`
                    tasks.create("lol", Closure.IDENTITY);
                    // BUG: Diagnostic contains: use `.register`
                    tasks.create(Map.of("name", "lol", "type", Task.class));
                    // BUG: Diagnostic contains: use `.register`
                    tasks.create(Map.of("name", "lol", "type", Task.class), Closure.IDENTITY);
                }
            }
            """)
                .addOutputLines(
                        "Test.java",
                        // language=java
                        """
            import groovy.lang.Closure;
            import java.util.Map;
            import org.gradle.api.Task;
            import org.gradle.api.tasks.TaskContainer;

            class Test {
                static void unused_return_value(TaskContainer tasks) {
                    tasks.register("lol");
                    tasks.register("lol", Task.class);
                    tasks.register("lol", Task.class, new Object());
                    tasks.register("lol", Task.class, task -> {});
                }

                static Task used_return_value(TaskContainer tasks) {
                    // BUG: Diagnostic contains: use `.register`
                    Task task = tasks.create("lol");
                    // BUG: Diagnostic contains: use `.register`
                    System.out.println(tasks.create("lol", Task.class, t -> {}));
                    // BUG: Diagnostic contains: use `.register`
                    return tasks.create("lol", Task.class);
                }

                static void methods_with_no_directly_equivalent_register(TaskContainer tasks) {
                    // BUG: Diagnostic contains: use `.register`
                    tasks.create("lol", Closure.IDENTITY);
                    // BUG: Diagnostic contains: use `.register`
                    tasks.create(Map.of("name", "lol", "type", Task.class));
                    // BUG: Diagnostic contains: use `.register`
                    tasks.create(Map.of("name", "lol", "type", Task.class), Closure.IDENTITY);
                }
            }
            """)
                .doTest();
    }
}
