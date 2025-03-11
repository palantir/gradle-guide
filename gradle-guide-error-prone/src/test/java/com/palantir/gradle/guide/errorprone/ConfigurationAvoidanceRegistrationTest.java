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
    void when_return_value_is_unused_just_use_register_immediately() {
        bestEffortRefactoringValidator()
                .addInputLines(
                        "Test.java",
                        // language=java
                        """
            import org.gradle.api.Task;
            import org.gradle.api.tasks.TaskContainer;

            class Test {
                static void unused_return_value(TaskContainer tasks) {
                    tasks.create("lol");
                    tasks.create("lol", Task.class);
                    tasks.create("lol", Task.class, new Object());
                    tasks.create("lol", Task.class, task -> {});
                }
            }
            """)
                .addOutputLines(
                        "Test.java",
                        // language=java
                        """
            import org.gradle.api.Task;
            import org.gradle.api.tasks.TaskContainer;

            class Test {
                static void unused_return_value(TaskContainer tasks) {
                    tasks.register("lol");
                    tasks.register("lol", Task.class);
                    tasks.register("lol", Task.class, new Object());
                    tasks.register("lol", Task.class, task -> {});
                }
            }
            """)
                .doTest();
    }

    @Test
    void when_return_value_is_used_adds_get_calls_to_usages() {
        bestEffortRefactoringValidator()
                .addInputLines(
                        "Test.java",
                        // language=java
                        """
            import org.gradle.api.Task;
            import org.gradle.api.tasks.TaskContainer;

            class Test {
                static Task used_return_value(TaskContainer tasks) {
                    // BUG: Diagnostic contains: use `.register`
                    Task task = tasks.create("lol");
                    // BUG: Diagnostic contains: use `.register`
                    System.out.println(tasks.create("lol", Task.class, t -> {}));
                    // BUG: Diagnostic contains: use `.register`
                    return tasks.create("lol", Task.class);
                }
            }
            """)
                .addOutputLines(
                        "Test.java",
                        // language=java
                        """
            import org.gradle.api.Task;
            import org.gradle.api.tasks.TaskContainer;
            import org.gradle.api.tasks.TaskProvider;

            class Test {
                static Task used_return_value(TaskContainer tasks) {
                    TaskProvider<Task> task = tasks.register("lol");
                    System.out.println(tasks.register("lol", Task.class, t -> {}).get());
                    return tasks.register("lol", Task.class).get();
                }
            }
            """)
                .doTest();
    }

    @Test
    void doesnt_touch_calls_with_no_directly_equivalent_register() {
        bestEffortRefactoringValidator()
                .addInputLines(
                        "Test.java",
                        // language=java
                        """
            import groovy.lang.Closure;
            import java.util.Map;
            import org.gradle.api.Task;
            import org.gradle.api.tasks.TaskContainer;

            class Test {
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
                .expectUnchanged()
                .doTest();
    }

    @Test
    void best_effort_refactors_usages_of_configurations_create_where_the_return_value_is_unused_to_register() {
        bestEffortRefactoringValidator()
                .addInputLines(
                        "Test.java",
                        // language=java
                        """
            import groovy.lang.Closure;
            import org.gradle.api.artifacts.Configuration;
            import org.gradle.api.artifacts.ConfigurationContainer;

            class Test {
                static void unused_return_value(ConfigurationContainer configurations) {
                    configurations.create("lol");
                    configurations.create("lol", conf -> {});
                }

                static Configuration used_return_value(ConfigurationContainer configurations) {
                    // BUG: Diagnostic contains: use `.register`
                    Configuration configuration = configurations.create("lol");
                    // BUG: Diagnostic contains: use `.register`
                    System.out.println(configurations.create("lol", conf -> {}));
                    // BUG: Diagnostic contains: use `.register`
                    return configurations.create("lol", conf -> {});
                }

                static void methods_with_no_directly_equivalent_register(ConfigurationContainer configurations) {
                    // BUG: Diagnostic contains: use `.register`
                    configurations.create("lol", Closure.IDENTITY);
                }
            }
            """)
                .addOutputLines(
                        "Test.java",
                        // language=java
                        """
            import groovy.lang.Closure;
            import org.gradle.api.NamedDomainObjectProvider;
            import org.gradle.api.artifacts.Configuration;
            import org.gradle.api.artifacts.ConfigurationContainer;

            class Test {
                static void unused_return_value(ConfigurationContainer configurations) {
                    configurations.register("lol");
                    configurations.register("lol", conf -> {});
                }

                static Configuration used_return_value(ConfigurationContainer configurations) {
                    NamedDomainObjectProvider<Configuration> configuration = configurations.register("lol");
                    System.out.println(configurations.register("lol", conf -> {}).get());
                    return configurations.register("lol", conf -> {}).get();
                }

                static void methods_with_no_directly_equivalent_register(ConfigurationContainer configurations) {
                    // BUG: Diagnostic contains: use `.register`
                    configurations.create("lol", Closure.IDENTITY);
                }
            }
            """)
                .doTest();
    }

    @Test
    void best_effort_mode_uses_get_on_taskprovider_usages() {
        bestEffortRefactoringValidator()
                .addInputLines(
                        "Test.java",
                        // language=java
                        """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskContainer;

            class Test {
                class CustomTask extends DefaultTask {}
                static CustomTask test(TaskContainer tasks) {
                    CustomTask task = tasks.create("lol", CustomTask.class);
                    String description = task.getDescription();
                    return task;
                }
            }
            """)
                .addOutputLines(
                        "Test.java",
                        // language=java
                        """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskContainer;
            import org.gradle.api.tasks.TaskProvider;

            class Test {
                class CustomTask extends DefaultTask {}
                static CustomTask test(TaskContainer tasks) {
                    TaskProvider<CustomTask> task = tasks.register("lol", CustomTask.class);
                    String description = task.get().getDescription();
                    return task.get();
                }
            }
            """)
                .doTest();
    }

    private RefactoringValidator bestEffortRefactoringValidator() {
        return refactoringValidator("-XepOpt:GradleGuide:BestEffortMode");
    }

    private RefactoringValidator refactoringValidator(String... args) {
        return RefactoringValidator.of(ConfigurationAvoidanceRegistration.class, getClass(), args);
    }
}
