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

import com.palantir.gradle.guide.errorprone.laziness.PrivateMethodsShouldTakeProviders;
import com.palantir.gradle.guide.helpers.RefactoringValidator;
import org.junit.jupiter.api.Test;

class PrivateMethodsShouldTakeProvidersTest {

    @Test
    void basic_case() {
        bestEffortRefactoringValidator()
                .addInputLines(
                        "Test.java",
                        // language=java
                        """
                import org.gradle.api.DefaultTask;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    private void helperMethod(DefaultTask task) {
                        task.setDescription("helper");
                        task.setGroup("test");
                    }

                    void usage(TaskProvider<DefaultTask> taskProvider) {
                        helperMethod(taskProvider.get());
                    }
                }
                """)
                .addOutputLines(
                        "Test.java",
                        // language=java
                        """
                import org.gradle.api.DefaultTask;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    private void helperMethod(TaskProvider<DefaultTask> task) {
                        task.get().setDescription("helper");
                        task.get().setGroup("test");
                    }

                    void usage(TaskProvider<DefaultTask> taskProvider) {
                        helperMethod(taskProvider);
                    }
                }
                """)
                .doTest();
    }

    @Test
    void custom_task() {
        bestEffortRefactoringValidator()
                .addInputLines(
                        "Test.java",
                        // language=java
                        """
                import org.gradle.api.DefaultTask;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    class CustomTask extends DefaultTask {
                        void customMethod() {}
                    }

                    private void helperMethod(CustomTask task) {
                        task.customMethod();
                    }

                    void usage(TaskProvider<CustomTask> taskProvider) {
                        helperMethod(taskProvider.get());
                    }
                }
                """)
                .addOutputLines(
                        "Test.java",
                        // language=java
                        """
                import org.gradle.api.DefaultTask;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    class CustomTask extends DefaultTask {
                        void customMethod() {}
                    }

                    private void helperMethod(TaskProvider<CustomTask> task) {
                        task.get().customMethod();
                    }

                    void usage(TaskProvider<CustomTask> taskProvider) {
                        helperMethod(taskProvider);
                    }
                }
                """)
                .doTest();
    }

    @Test
    void multiple_task_parameters() {
        bestEffortRefactoringValidator()
                .addInputLines(
                        "Test.java",
                        // language=java
                        """
                import org.gradle.api.Task;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    private void refactorOne(Task first, Task second) {
                        first.dependsOn(second);
                        second.setDescription("dependency");
                    }

                    private void refactorBoth(Task first, Task second) {
                        first.dependsOn(second);
                        second.setDescription("dependency");
                    }

                    void usage(TaskProvider<Task> task1, TaskProvider<Task> task2, Task task3) {
                        refactorOne(task3, task2.get());
                        refactorBoth(task1.get(), task2.get());
                    }
                }
                """)
                .addOutputLines(
                        "Test.java",
                        // language=java
                        """
                import org.gradle.api.Task;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    private void refactorOne(Task first, TaskProvider<Task> second) {
                        first.dependsOn(second.get());
                        second.get().setDescription("dependency");
                    }

                    private void refactorBoth(TaskProvider<Task> first, TaskProvider<Task> second) {
                        first.get().dependsOn(second.get());
                        second.get().setDescription("dependency");
                    }

                    void usage(TaskProvider<Task> task1, TaskProvider<Task> task2, Task task3) {
                        refactorOne(task3, task2);
                        refactorBoth(task1, task2);
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
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    private void configureTask(Task task) {
                        task.setGroup("utilities");
                    }

                    void usage1(TaskProvider<Task> taskProvider) {
                        configureTask(taskProvider.get());
                    }

                    void usage2(TaskProvider<Task> anotherProvider) {
                        configureTask(anotherProvider.get());
                    }
                }
                """)
                .addOutputLines(
                        "Test.java",
                        // language=java
                        """
                import org.gradle.api.Task;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    private void configureTask(TaskProvider<Task> task) {
                        task.get().setGroup("utilities");
                    }

                    void usage1(TaskProvider<Task> taskProvider) {
                        configureTask(taskProvider);
                    }

                    void usage2(TaskProvider<Task> anotherProvider) {
                        configureTask(anotherProvider);
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
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    public void publicMethod(Task task) {
                        task.setGroup("public");
                    }

                    void usage(TaskProvider<Task> taskProvider) {
                        publicMethod(taskProvider.get());
                    }
                }
                """)
                .expectUnchanged()
                .doTest();
    }

    @Test
    void doesnt_refactor_when_not_all_args_are_provider_get() {
        bestEffortRefactoringValidator()
                .addInputLines(
                        "Test.java",
                        // language=java
                        """
                import org.gradle.api.Task;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    private void helperMethod(Task task) {
                        task.setGroup("helper");
                    }

                    void usage(TaskProvider<Task> taskProvider, Task regularTask) {
                        helperMethod(taskProvider.get());
                        helperMethod(regularTask);
                    }
                }
                """)
                .expectUnchanged()
                .doTest();
    }

    @Test
    void handles_mixed_parameters() {
        bestEffortRefactoringValidator()
                .addInputLines(
                        "Test.java",
                        // language=java
                        """
                import org.gradle.api.Task;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    private void mixedMethod(Task task, String name) {
                        task.setGroup(name);
                    }

                    void usage(TaskProvider<Task> taskProvider) {
                        mixedMethod(taskProvider.get(), "test");
                    }
                }
                """)
                .addOutputLines(
                        "Test.java",
                        // language=java
                        """
                import org.gradle.api.Task;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    private void mixedMethod(TaskProvider<Task> task, String name) {
                        task.get().setGroup(name);
                    }

                    void usage(TaskProvider<Task> taskProvider) {
                        mixedMethod(taskProvider, "test");
                    }
                }
                """)
                .doTest();
    }

    private RefactoringValidator bestEffortRefactoringValidator() {
        return RefactoringValidator.of(
                PrivateMethodsShouldTakeProviders.class, getClass(), "-XepOpt:GradleGuide:BestEffortMode");
    }
}