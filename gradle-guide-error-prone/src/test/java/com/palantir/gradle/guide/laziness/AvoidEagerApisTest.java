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
package com.palantir.gradle.guide.laziness;

import com.google.errorprone.CompilationTestHelper;
import com.palantir.gradle.guide.errorprone.laziness.AvoidEagerApis;
import com.palantir.gradle.guide.helpers.RefactoringValidator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AvoidEagerApisTest {
    private final CompilationTestHelper compilationTestHelper =
            CompilationTestHelper.newInstance(AvoidEagerApis.class, getClass());

    @SuppressWarnings("for-rollout:MisformattedTestData")
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
                        // BUG: Diagnostic contains: Use `.register`
                        tasks.create(Map.of("name", "lol", "type", Task.class));
                        // BUG: Diagnostic contains: Use `.register`
                        tasks.create(Map.of("name", "lol", "type", Task.class), Closure.IDENTITY);
                        // BUG: Diagnostic contains: Use `.register`
                        tasks.create("lol", Closure.IDENTITY);
                        // BUG: Diagnostic contains: Use `.register`
                        tasks.create("lol");
                        // BUG: Diagnostic contains: Use `.register`
                        tasks.create("lol", Task.class);
                        // BUG: Diagnostic contains: Use `.register`
                        tasks.create("lol", Task.class, new Object());
                        // BUG: Diagnostic contains: Use `.register`
                        tasks.create("lol", Task.class, task -> {});
                    }
                }
                """);

        compilationTestHelper.doTest();
    }

    @SuppressWarnings("for-rollout:MisformattedTestData")
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
                        // BUG: Diagnostic contains: Use `.register`
                        configurations.create("lol");
                        // BUG: Diagnostic contains: Use `.register`
                        configurations.create("lol", Closure.IDENTITY);
                        // BUG: Diagnostic contains: Use `.register`
                        configurations.create("lol", conf -> {});
                    }
                }
                """);

        compilationTestHelper.doTest();
    }

    @SuppressWarnings("for-rollout:MisformattedTestData")
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
                        // BUG: Diagnostic contains: Use `.register`
                        sourceSets.create("lol");
                        // BUG: Diagnostic contains: Use `.register`
                        sourceSets.create("lol", Closure.IDENTITY);
                        // BUG: Diagnostic contains: Use `.register`
                        sourceSets.create("lol", conf -> {});
                    }
                }
                """);

        compilationTestHelper.doTest();
    }

    @SuppressWarnings("for-rollout:MisformattedTestData")
    @Test
    void matches_eager_task_container_apis() {
        compilationTestHelper.addSourceLines(
                "Test.java",
                // language=java
                """
                import org.gradle.api.Task;
                import org.gradle.api.tasks.TaskContainer;

                class Test {
                    static void test(TaskContainer tasks) {
                        // BUG: Diagnostic contains: Use `.register`
                        tasks.create("foo");
                        // BUG: Diagnostic contains: Use `.named(...)` instead of `.getByName(...)`
                        tasks.getByName("foo");
                        // BUG: Diagnostic contains: Use `.named(...)` instead of `.findByName(...)`
                        tasks.findByName("foo");
                        // BUG: Diagnostic contains: Use `.named(...)` instead of `.getByPath(...)`
                        tasks.getByPath(":foo");
                        // BUG: Diagnostic contains: Use `.named(...)` instead of `.findByPath(...)`
                        tasks.findByPath(":foo");
                        // BUG: Diagnostic contains: Avoid `.replace()` - forces eager resolution and behavior may change
                        tasks.replace("foo", Task.class);
                        // BUG: Diagnostic contains: Use `.configureEach(...)` instead of `.whenTaskAdded(...)`
                        tasks.whenTaskAdded(task -> {});
                    }
                }
                """);

        compilationTestHelper.doTest();
    }

    @SuppressWarnings("for-rollout:MisformattedTestData")
    @Test
    void matches_eager_task_collection_apis() {
        compilationTestHelper.addSourceLines(
                "Test.java",
                // language=java
                """
                import org.gradle.api.Task;
                import org.gradle.api.tasks.TaskCollection;

                class Test {
                    static void test(TaskCollection<Task> tasks) {
                        // BUG: Diagnostic contains: Use `.configureEach(...)` with conditional logic instead of `.matching(...)`
                        tasks.matching(task -> true);
                        // BUG: Diagnostic contains: Use `.named(...)` instead of `.getAt(...)`
                        tasks.getAt("foo");
                        // BUG: Diagnostic contains: Use `.configureEach(...)` instead of `.all(...)`
                        tasks.all(task -> {});
                    }
                }
                """);

        compilationTestHelper.doTest();
    }

    @SuppressWarnings("for-rollout:MisformattedTestData")
    @Test
    void matches_eager_domain_object_collection_apis() {
        compilationTestHelper.addSourceLines(
                "Test.java",
                // language=java
                """
                import org.gradle.api.DomainObjectCollection;

                class Test {
                    static void test(DomainObjectCollection<Object> objects) {
                        // BUG: Diagnostic contains: Use `.configureEach(...)` instead of `.whenObjectAdded(...)`
                        objects.whenObjectAdded(obj -> {});
                        // BUG: Diagnostic contains: Use `.configureEach(...)` instead of `.all(...)`
                        objects.all(obj -> {});
                        // BUG: Diagnostic contains: Use `.configureEach(...)` with conditional logic instead of `.matching(...)`
                        objects.matching(obj -> true);
                        // BUG: Diagnostic contains: Use `.named(...).configureEach(...)` instead of `.withType(...)`
                        objects.withType(String.class);
                    }
                }
                """);

        compilationTestHelper.doTest();
    }

    @SuppressWarnings("for-rollout:MisformattedTestData")
    @Test
    void matches_eager_named_domain_object_set_apis() {
        compilationTestHelper.addSourceLines(
                "Test.java",
                // language=java
                """
                import org.gradle.api.NamedDomainObjectSet;
                import groovy.lang.Closure;

                class Test {
                    static void test(NamedDomainObjectSet<Object> objects) {
                        // BUG: Diagnostic contains: Use `.matching(...)` and `.configureEach(...)` instead of `.findAll()
                        objects.findAll(Closure.IDENTITY);
                        // BUG: Diagnostic contains: Use `.named()` to avoid eager configuration. Note that it fails if the task does not exist
                        objects.findByName("foo");
                    }
                }
                """);

        compilationTestHelper.doTest();
    }

    @Nested
    class BestEffortMode {
        @Nested
        class Tasks {
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
                                    static void test(TaskContainer tasks) {
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
                                    static void test(TaskContainer tasks) {
                                        tasks.register("lol").get();
                                        tasks.register("lol", Task.class).get();
                                        tasks.register("lol", Task.class, new Object()).get();
                                        tasks.register("lol", Task.class, task -> {}).get();
                                    }
                                }
                                """)
                        .doTest();
            }

            @Test
            void when_return_value_is_used_adds_get_call() {
                bestEffortRefactoringValidator()
                        .addInputLines(
                                "Test.java",
                                // language=java
                                """
                                import org.gradle.api.Task;
                                import org.gradle.api.tasks.TaskContainer;

                                class Test {
                                    static Task test(TaskContainer tasks) {
                                        // BUG: Diagnostic contains: Use `.register`
                                        Task task = tasks.create("lol");
                                        // BUG: Diagnostic contains: Use `.register`
                                        System.out.println(tasks.create("lol", Task.class, t -> {}));
                                        // BUG: Diagnostic contains: Use `.register`
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

                                class Test {
                                    static Task test(TaskContainer tasks) {
                                        Task task = tasks.register("lol").get();
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
                                    static void test(TaskContainer tasks) {
                                        // BUG: Diagnostic contains: Use `.register`
                                        tasks.create("lol", Closure.IDENTITY);
                                        // BUG: Diagnostic contains: Use `.register`
                                        tasks.create(Map.of("name", "lol", "type", Task.class));
                                        // BUG: Diagnostic contains: Use `.register`
                                        tasks.create(Map.of("name", "lol", "type", Task.class), Closure.IDENTITY);
                                    }
                                }
                                """)
                        .expectUnchanged()
                        .doTest();
            }

            @Test
            void getByName_refactored_correctly() {
                bestEffortRefactoringValidator()
                        .addInputLines(
                                "Test.java",
                                // language=java
                                """
                                import org.gradle.api.DefaultTask;
                                import org.gradle.api.Project;
                                import org.gradle.api.tasks.TaskContainer;

                                class Test {
                                    class CustomTask extends DefaultTask {}
                                    static void test(Project project) {
                                        CustomTask customTask = (CustomTask) project.getTasks().getByName("lol");

                                        TaskContainer tasks = project.getTasks();
                                        CustomTask customTask2 = (CustomTask) tasks.getByName("lol");

                                        CustomTask wontRemoveCast = (CustomTask) project.getTasks().getByName("lol").getDependsOn().stream().findFirst().get();
                                    }
                                }
                                """)
                        .addOutputLines(
                                "Test.java",
                                // language=java
                                """
                                import org.gradle.api.DefaultTask;
                                import org.gradle.api.Project;
                                import org.gradle.api.tasks.TaskContainer;

                                class Test {
                                    class CustomTask extends DefaultTask {}
                                    static void test(Project project) {
                                        CustomTask customTask = project.getTasks().named("lol", CustomTask.class).get();

                                        TaskContainer tasks = project.getTasks();
                                        CustomTask customTask2 = tasks.named("lol", CustomTask.class).get();

                                        CustomTask wontRemoveCast = (CustomTask) project.getTasks().named("lol").get().getDependsOn().stream().findFirst().get();
                                    }
                                }
                                """)
                        .doTest();
            }
        }

        @Nested
        class Configurations {
            @Test
            void unused_return_value() {
                bestEffortRefactoringValidator()
                        .addInputLines(
                                "Test.java",
                                // language=java
                                """
                                import org.gradle.api.artifacts.ConfigurationContainer;

                                class Test {
                                    static void test(ConfigurationContainer configurations) {
                                        configurations.create("lol");
                                        configurations.create("lol", conf -> {});
                                    }
                                }
                                """)
                        .addOutputLines(
                                "Test.java",
                                // language=java
                                """
                                import org.gradle.api.artifacts.ConfigurationContainer;

                                class Test {
                                    static void test(ConfigurationContainer configurations) {
                                        configurations.register("lol").get();
                                        configurations.register("lol", conf -> {}).get();
                                    }
                                }
                                """)
                        .doTest();
            }

            @Test
            void used_return_value() {
                bestEffortRefactoringValidator()
                        .addInputLines(
                                "Test.java",
                                // language=java
                                """
                                import org.gradle.api.artifacts.Configuration;
                                import org.gradle.api.artifacts.ConfigurationContainer;

                                class Test {
                                    static Configuration test(ConfigurationContainer configurations) {
                                        // BUG: Diagnostic contains: Use `.register`
                                        Configuration configuration = configurations.create("lol");
                                        // BUG: Diagnostic contains: Use `.register`
                                        System.out.println(configurations.create("lol", conf -> {}));
                                        // BUG: Diagnostic contains: Use `.register`
                                        return configurations.create("lol", conf -> {});
                                    }
                                }
                                """)
                        .addOutputLines(
                                "Test.java",
                                // language=java
                                """
                                import org.gradle.api.artifacts.Configuration;
                                import org.gradle.api.artifacts.ConfigurationContainer;

                                class Test {
                                    static Configuration test(ConfigurationContainer configurations) {
                                        Configuration configuration = configurations.register("lol").get();
                                        System.out.println(configurations.register("lol", conf -> {}).get());
                                        return configurations.register("lol", conf -> {}).get();
                                    }
                                }
                                """)
                        .doTest();
            }

            @Test
            void methods_with_no_directly_equivalent_register() {
                bestEffortRefactoringValidator()
                        .addInputLines(
                                "Test.java",
                                // language=java
                                """
                                import groovy.lang.Closure;
                                import org.gradle.api.artifacts.ConfigurationContainer;

                                class Test {
                                    static void test(ConfigurationContainer configurations) {
                                        // BUG: Diagnostic contains: Use `.register`
                                        configurations.create("lol", Closure.IDENTITY);
                                    }
                                }
                                """)
                        .expectUnchanged()
                        .doTest();
            }
        }
    }

    private RefactoringValidator bestEffortRefactoringValidator() {
        return refactoringValidator("-XepOpt:GradleGuide:BestEffortMode");
    }

    private RefactoringValidator refactoringValidator(String... args) {
        return RefactoringValidator.of(AvoidEagerApis.class, getClass(), args);
    }
}
