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

import com.palantir.gradle.guide.errorprone.laziness.PrivateMethodsShouldReturnProviders;
import com.palantir.gradle.guide.helpers.RefactoringValidator;
import org.junit.jupiter.api.Test;

class PrivateMethodsShouldReturnProvidersTest {

    @Test
    void basic_case() {
        bestEffortRefactoringValidator()
                .addInputLines(
                        "Test.java",
                        // language=java
                        """
                import org.gradle.api.DefaultTask;
                import org.gradle.api.provider.Provider;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    private static DefaultTask getTask(TaskProvider<DefaultTask> taskProvider) {
                        return taskProvider.get();
                    }

                    void usage(TaskProvider<DefaultTask> taskProvider) {
                        DefaultTask task = getTask(taskProvider);
                        task.setDescription("test");
                    }
                }
                """)
                .addOutputLines(
                        "Test.java",
                        // language=java
                        """
                import org.gradle.api.DefaultTask;
                import org.gradle.api.provider.Provider;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    private static Provider<DefaultTask> getTask(TaskProvider<DefaultTask> taskProvider) {
                        return taskProvider;
                    }

                    void usage(TaskProvider<DefaultTask> taskProvider) {
                        DefaultTask task = getTask(taskProvider).get();
                        task.setDescription("test");
                    }
                }
                """)
                .doTest();
    }

    @Test
    void multiple_return_statements() {
        bestEffortRefactoringValidator()
                .addInputLines(
                        "Test.java",
                        // language=java
                        """
                import org.gradle.api.Task;
                import org.gradle.api.provider.Provider;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    private Task chooseTask(TaskProvider<Task> first, TaskProvider<Task> second, boolean useFirst) {
                        if (useFirst) {
                            return first.get();
                        }
                        return second.get();
                    }

                    void usage(TaskProvider<Task> task1, TaskProvider<Task> task2) {
                        Task chosen = chooseTask(task1, task2, true);
                        chosen.setGroup("chosen");
                    }
                }
                """)
                .addOutputLines(
                        "Test.java",
                        // language=java
                        """
                import org.gradle.api.Task;
                import org.gradle.api.provider.Provider;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    private Provider<Task> chooseTask(TaskProvider<Task> first, TaskProvider<Task> second, boolean useFirst) {
                        if (useFirst) {
                            return first;
                        }
                        return second;
                    }

                    void usage(TaskProvider<Task> task1, TaskProvider<Task> task2) {
                        Task chosen = chooseTask(task1, task2, true).get();
                        chosen.setGroup("chosen");
                    }
                }
                """)
                .doTest();
    }

    @Test
    void multiple_call_sites() {
        bestEffortRefactoringValidator()
                .addInputLines(
                        "Test.java",
                        // language=java
                        """
                import org.gradle.api.Task;
                import org.gradle.api.provider.Provider;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    private Task unwrapTask(TaskProvider<Task> taskProvider) {
                        return taskProvider.get();
                    }

                    void usage1(TaskProvider<Task> taskProvider) {
                        Task task = unwrapTask(taskProvider);
                        task.setGroup("group1");
                    }

                    void usage2(TaskProvider<Task> anotherProvider) {
                        Task task = unwrapTask(anotherProvider);
                        task.setDescription("description2");
                    }
                }
                """)
                .addOutputLines(
                        "Test.java",
                        // language=java
                        """
                import org.gradle.api.Task;
                import org.gradle.api.provider.Provider;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    private Provider<Task> unwrapTask(TaskProvider<Task> taskProvider) {
                        return taskProvider;
                    }

                    void usage1(TaskProvider<Task> taskProvider) {
                        Task task = unwrapTask(taskProvider).get();
                        task.setGroup("group1");
                    }

                    void usage2(TaskProvider<Task> anotherProvider) {
                        Task task = unwrapTask(anotherProvider).get();
                        task.setDescription("description2");
                    }
                }
                """)
                .doTest();
    }

    @Test
    void doesnt_refactor_public_methods() {
        bestEffortRefactoringValidator()
                .addInputLines(
                        "Test.java",
                        // language=java
                        """
                import org.gradle.api.Task;
                import org.gradle.api.provider.Provider;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    public Task publicMethod(TaskProvider<Task> taskProvider) {
                        return taskProvider.get();
                    }

                    void usage(TaskProvider<Task> taskProvider) {
                        Task task = publicMethod(taskProvider);
                        task.setGroup("public");
                    }
                }
                """)
                .expectUnchanged()
                .doTest();
    }

    @Test
    void doesnt_refactor_when_not_all_returns_are_provider_get() {
        bestEffortRefactoringValidator()
                .addInputLines(
                        "Test.java",
                        // language=java
                        """
                import org.gradle.api.Task;
                import org.gradle.api.provider.Provider;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    private Task mixedReturns(TaskProvider<Task> taskProvider, Task regularTask, boolean useProvider) {
                        if (useProvider) {
                            return taskProvider.get();
                        }
                        return regularTask;
                    }

                    void usage(TaskProvider<Task> taskProvider, Task task) {
                        Task result = mixedReturns(taskProvider, task, true);
                        result.setGroup("mixed");
                    }
                }
                """)
                .expectUnchanged()
                .doTest();
    }

    @Test
    void doesnt_refactor_methods_without_usages() {
        bestEffortRefactoringValidator()
                .addInputLines(
                        "Test.java",
                        // language=java
                        """
                import org.gradle.api.Task;
                import org.gradle.api.provider.Provider;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    private Task unusedMethod(TaskProvider<Task> taskProvider) {
                        return taskProvider.get();
                    }
                }
                """)
                .expectUnchanged()
                .doTest();
    }

    @Test
    void doesnt_refactor_void_methods() {
        bestEffortRefactoringValidator()
                .addInputLines(
                        "Test.java",
                        // language=java
                        """
                import org.gradle.api.Task;
                import org.gradle.api.provider.Provider;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    private void voidMethod(TaskProvider<Task> taskProvider) {
                        taskProvider.get().setGroup("void");
                        return;
                    }

                    void usage(TaskProvider<Task> taskProvider) {
                        voidMethod(taskProvider);
                    }
                }
                """)
                .expectUnchanged()
                .doTest();
    }

    @Test
    void custom_provider_type() {
        bestEffortRefactoringValidator()
                .addInputLines(
                        "Test.java",
                        // language=java
                        """
                import org.gradle.api.provider.Provider;

                class Test {
                    private String getValue(Provider<String> provider) {
                        return provider.get();
                    }

                    void usage(Provider<String> stringProvider) {
                        String value = getValue(stringProvider);
                        System.out.println(value);
                    }
                }
                """)
                .addOutputLines(
                        "Test.java",
                        // language=java
                        """
                import org.gradle.api.provider.Provider;

                class Test {
                    private Provider<String> getValue(Provider<String> provider) {
                        return provider;
                    }

                    void usage(Provider<String> stringProvider) {
                        String value = getValue(stringProvider).get();
                        System.out.println(value);
                    }
                }
                """)
                .doTest();
    }

    private RefactoringValidator bestEffortRefactoringValidator() {
        return RefactoringValidator.of(
                PrivateMethodsShouldReturnProviders.class, getClass(), "-XepOpt:GradleGuide:BestEffortMode");
    }
}