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

package com.palantir.gradle.guide.besteffort;

import com.palantir.gradle.guide.errorprone.laziness.AvoidEagerlyEvaluatingProviders;
import com.palantir.gradle.guide.helpers.RefactoringValidator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AvoidEagerlyEvaluatingProvidersTest {
    @Nested
    class ProviderGetNotBeingUsedForAnything {
        @Test
        void remove_get_from_task_provider() {
            // language=Java
            refactorFromTo(
                    """
                import org.gradle.api.Project;
                import org.gradle.api.provider.Provider;
                import org.gradle.api.tasks.TaskContainer;

                class Test {
                    void test(Project project, Provider<String> provider) {
                        project.getTasks().register("myTask").get();

                        TaskContainer tasks = project.getTasks();
                        tasks.register("yourTask").get();
                    }
                }
                """,
                    """
                import org.gradle.api.Project;
                import org.gradle.api.provider.Provider;
                import org.gradle.api.tasks.TaskContainer;

                class Test {
                    void test(Project project, Provider<String> provider) {
                        project.getTasks().register("myTask");

                        TaskContainer tasks = project.getTasks();
                        tasks.register("yourTask");
                    }
                }
                """);
        }

        @Test
        void remove_get_from_generic_provider() {
            // language=Java
            refactorFromTo(
                    """
                import org.gradle.api.Project;
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Project project, Provider<String> provider) {
                        project.getConfigurations().register("happySquirrel").get();
                        provider.map(value -> value + "squirrel").get();
                    }
                }
                """,
                    """
                import org.gradle.api.Project;
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Project project, Provider<String> provider) {
                        project.getConfigurations().register("happySquirrel");
                        provider.map(value -> value + "squirrel");
                    }
                }
                """);
        }

        @Test
        void dont_remove_get_if_it_fails_compilation() {
            // language=Java
            expectUnchanged(
                    """
                import org.gradle.api.Project;
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Project project, Provider<String> provider) {
                        // Unfortunately, `provider;` isn't a valid java statement
                        // BUG: Diagnostic contains: Do not call `Provider.get`
                        provider.get();

                        // BUG: Diagnostic contains: Do not call `Provider.get`
                        String value = provider.get();
                    }
                }
                """);
        }
    }

    @Nested
    class TaskProviderGetUsedAsArg {

        @Test
        void depends_on_task_provider_get() {
            // language=Java
            refactorFromTo(
                    """
                import org.gradle.api.Task;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    class CustomTask extends org.gradle.api.DefaultTask {}

                    void test(Task task, TaskProvider<?> provider1, TaskProvider<?> provider2, CustomTask customTask) {
                        task.dependsOn(provider1.get());
                        customTask.dependsOn(provider1.get());
                        task.dependsOn(provider1.get(), provider2.get(), customTask);
                    }
                }
                """,
                    """
                import org.gradle.api.Task;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    class CustomTask extends org.gradle.api.DefaultTask {}

                    void test(Task task, TaskProvider<?> provider1, TaskProvider<?> provider2, CustomTask customTask) {
                        task.dependsOn(provider1);
                        customTask.dependsOn(provider1);
                        task.dependsOn(provider1, provider2, customTask);
                    }
                }
                """);
        }
    }

    @Nested
    class TaskProviderGetUsedToConfigure {
        @Test
        void move_task_configuration_inside_configure_lambda() {
            // language=Java
            refactorFromTo(
                    """
                import org.gradle.api.DefaultTask;
                import org.gradle.api.Project;
                import org.gradle.api.Task;
                import org.gradle.api.provider.Property;
                import org.gradle.api.provider.Provider;
                import org.gradle.api.tasks.Input;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    abstract class CustomTask extends DefaultTask {
                        @Input
                        public abstract Property<String> getInput();
                    }

                    void test(Project project, Task other, TaskProvider<? extends CustomTask> customTask) {
                        customTask.get().getInput().set("happySquirrel");
                        customTask.get().dependsOn(other);

                        Provider<String> provider = project.provider(() -> "Squirrel");
                        customTask.get().getInput().set(provider.map(val -> "happy" + val));
                    }
                }
                """,
                    """
                import org.gradle.api.DefaultTask;
                import org.gradle.api.Project;
                import org.gradle.api.Task;
                import org.gradle.api.provider.Property;
                import org.gradle.api.provider.Provider;
                import org.gradle.api.tasks.Input;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    abstract class CustomTask extends DefaultTask {
                        @Input
                        public abstract Property<String> getInput();
                    }

                    void test(Project project, Task other, TaskProvider<? extends CustomTask> customTask) {
                        customTask.configure(customTaskValue -> customTaskValue.getInput().set("happySquirrel"));
                        customTask.configure(customTaskInner -> customTaskInner.dependsOn(other));

                        Provider<String> provider = project.provider(() -> "Squirrel");
                        customTask.configure(customTaskItem -> customTaskItem.getInput().set(provider.map(val -> "happy" + val)));
                    }
                }
                """);
        }
    }

    @Nested
    class ProviderGetBeingAssignedToVariable {

        @Test
        void changes_task_variable_to_taskprovider() {
            // language=Java
            refactorFromTo(
                    """
                import org.gradle.api.Task;
                import org.gradle.api.tasks.TaskContainer;

                class Test {
                    static Task test(TaskContainer tasks) {
                        Task task = tasks.named("lol").get();
                        // BUG: Diagnostic contains: Do not call `Provider.get`.
                        String description = task.getDescription();
                        // BUG: Diagnostic contains: Do not call `Provider.get`.
                        return task;
                    }
                }
                """,
                    """
                import org.gradle.api.Task;
                import org.gradle.api.tasks.TaskContainer;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    static Task test(TaskContainer tasks) {
                        TaskProvider<Task> task = tasks.named("lol");
                        // BUG: Diagnostic contains: Do not call `Provider.get`.
                        String description = task.get().getDescription();
                        // BUG: Diagnostic contains: Do not call `Provider.get`.
                        return task.get();
                    }
                }
                """);
        }

        @Test
        void changes_custom_task_variable_to_taskprovider() {
            // language=Java
            refactorFromTo(
                    """
                import org.gradle.api.DefaultTask;
                import org.gradle.api.provider.Property;
                import org.gradle.api.tasks.Input;
                import org.gradle.api.tasks.TaskContainer;

                class Test {
                    abstract class CustomTask extends DefaultTask {
                        @Input
                        public abstract Property<String> getInput();
                    }
                    static CustomTask test(TaskContainer tasks) {
                        CustomTask task1 = tasks.named("task1", CustomTask.class).get();
                        CustomTask task2 = tasks.named("task2", CustomTask.class, task -> {
                            task.getInput().set("Happy squirrel");
                            // BUG: Diagnostic contains: Do not call `Provider.get`.
                            System.out.println(task1);
                        }).get();
                        // BUG: Diagnostic contains: Do not call `Provider.get`.
                        String description = task1.getDescription();
                        // BUG: Diagnostic contains: Do not call `Provider.get`.
                        return task1;
                    }
                }
                """,
                    """
                import org.gradle.api.DefaultTask;
                import org.gradle.api.provider.Property;
                import org.gradle.api.tasks.Input;
                import org.gradle.api.tasks.TaskContainer;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    abstract class CustomTask extends DefaultTask {
                        @Input
                        public abstract Property<String> getInput();
                    }
                    static CustomTask test(TaskContainer tasks) {
                        TaskProvider<CustomTask> task1 = tasks.named("task1", CustomTask.class);
                        TaskProvider<CustomTask> task2 = tasks.named("task2", CustomTask.class, task -> {
                            task.getInput().set("Happy squirrel");
                            // BUG: Diagnostic contains: Do not call `Provider.get`.
                            System.out.println(task1.get());
                        });
                        // BUG: Diagnostic contains: Do not call `Provider.get`.
                        String description = task1.get().getDescription();
                        // BUG: Diagnostic contains: Do not call `Provider.get`.
                        return task1.get();
                    }
                }
                """);
        }

        @Test
        void changes_configuration_variable_to_provider() {
            // language=Java
            refactorFromTo(
                    """
                import org.gradle.api.Project;
                import org.gradle.api.artifacts.Configuration;

                class Test {
                    static void test(Project project) {
                        Configuration config = project.getConfigurations().register("myConfig").get();
                        // BUG: Diagnostic contains: Do not call `Provider.get`.
                        System.out.println(config.getDescription());
                    }
                }
                """,
                    """
                import org.gradle.api.NamedDomainObjectProvider;
                import org.gradle.api.Project;
                import org.gradle.api.artifacts.Configuration;

                class Test {
                    static void test(Project project) {
                        NamedDomainObjectProvider<Configuration> config = project.getConfigurations().register("myConfig");
                        // BUG: Diagnostic contains: Do not call `Provider.get`.
                        System.out.println(config.get().getDescription());
                    }
                }
                """);
        }
    }

    @Nested
    class ChainedCallAlsoReturnsProviders {
        @Test
        void transform_chained_provider_methods() {
            // language=Java
            refactorFromTo(
                    """
                import java.io.File;
                import org.gradle.api.DefaultTask;
                import org.gradle.api.Project;
                import org.gradle.api.file.RegularFile;
                import org.gradle.api.file.RegularFileProperty;
                import org.gradle.api.provider.Property;
                import org.gradle.api.tasks.Input;
                import org.gradle.api.tasks.InputFile;
                import org.gradle.api.tasks.OutputFile;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    abstract class CustomTask extends DefaultTask {
                        @InputFile
                        public abstract RegularFileProperty getInput();
                    }
                    void test(TaskProvider<CustomTask> provider, Project project) {
                        // BUG: Diagnostic contains: Do not call `Provider.get`.
                        RegularFile regularFile = provider.get().getInput().get();

                        // BUG: Diagnostic contains: Do not call `Provider.get`.
                        RegularFileProperty regularFileProperty = provider.get().getInput();
                    }
                }
                """,
                    """
                import java.io.File;
                import org.gradle.api.DefaultTask;
                import org.gradle.api.Project;
                import org.gradle.api.file.RegularFile;
                import org.gradle.api.file.RegularFileProperty;
                import org.gradle.api.provider.Property;
                import org.gradle.api.tasks.Input;
                import org.gradle.api.tasks.InputFile;
                import org.gradle.api.tasks.OutputFile;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    abstract class CustomTask extends DefaultTask {
                        @InputFile
                        public abstract RegularFileProperty getInput();
                    }
                    void test(TaskProvider<CustomTask> provider, Project project) {
                        // BUG: Diagnostic contains: Do not call `Provider.get`.
                        RegularFile regularFile = provider.map(providerItem -> providerItem.getInput()).get().get();

                        // BUG: Diagnostic contains: Do not call `Provider.get`.
                        RegularFileProperty regularFileProperty = provider.map(providerData -> providerData.getInput()).get();
                    }
                }
                """);
        }

        @Test
        void dont_transform_non_provider_chained_methods() {
            // language=Java
            expectUnchanged(
                    """
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Provider<String> provider) {
                        // BUG: Diagnostic contains: Do not call `Provider.get`
                        String result = provider.get().toString();
                    }
                }
                """);
        }
    }

    @Nested
    class ConsumingMethodAlsoTakesProvider {
        @Test
        void transform_property_set_with_provider_get() {
            // language=Java
            refactorFromTo(
                    """
                import java.io.File;
                import org.gradle.api.DefaultTask;
                import org.gradle.api.file.RegularFileProperty;
                import org.gradle.api.provider.Property;
                import org.gradle.api.provider.Provider;
                import org.gradle.api.tasks.Input;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    void test(Property<String> property, Provider<String> provider) {
                        property.set(provider.get());
                    }

                    abstract class CustomTask extends DefaultTask {
                        @Input
                        public abstract Property<File> getInput();
                    }

                    void regularFileProperty(
                        RegularFileProperty fileProperty,
                        Provider<File> fileProvider,
                        RegularFileProperty fileProperty2,
                        TaskProvider<CustomTask> customTaskProvider) {

                        fileProperty.set(fileProvider.get());
                        fileProperty.set(fileProperty2.get());
                        // BUG: Diagnostic contains: Do not call `Provider.get`
                        fileProperty.fileProvider(customTaskProvider.map(task -> task.getInput()).get());
                    }
                }
                """,
                    """
                import java.io.File;
                import org.gradle.api.DefaultTask;
                import org.gradle.api.file.RegularFileProperty;
                import org.gradle.api.provider.Property;
                import org.gradle.api.provider.Provider;
                import org.gradle.api.tasks.Input;
                import org.gradle.api.tasks.TaskProvider;

                class Test {
                    void test(Property<String> property, Provider<String> provider) {
                        property.set(provider);
                    }

                    abstract class CustomTask extends DefaultTask {
                        @Input
                        public abstract Property<File> getInput();
                    }

                    void regularFileProperty(
                        RegularFileProperty fileProperty,
                        Provider<File> fileProvider,
                        RegularFileProperty fileProperty2,
                        TaskProvider<CustomTask> customTaskProvider) {

                        fileProperty.fileProvider(fileProvider);
                        fileProperty.set(fileProperty2);
                        // BUG: Diagnostic contains: Do not call `Provider.get`
                        fileProperty.fileProvider(customTaskProvider.map(task -> task.getInput()).get());
                    }
                }
                """);
        }

        @Test
        void dont_transform_non_terminal_calls() {
            // language=Java
            expectUnchanged(
                    """
                import org.gradle.api.provider.Property;
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Property<String> property, Provider<String> provider) {
                        // BUG: Diagnostic contains: Do not call `Provider.get`
                        property.set(provider.get().toString());
                    }
                }
                """);
        }

        @Test
        void dont_transform_methods_not_accepting_providers() {
            // language=Java
            expectUnchanged(
                    """
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Provider<String> provider) {
                        // BUG: Diagnostic contains: Do not call `Provider.get`
                        System.out.println(provider.get());
                    }
                }
                """);
        }
    }

    @Nested
    class ProviderGetInProviderInstantiation {
        @Test
        void basic_case() {
            // language=Java
            refactorFromTo(
                    """
                import org.gradle.api.Project;
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Project project, Provider<String> string) {
                        project.provider(() -> string.get() + " yo");
                    }
                }
                """,
                    """
                import org.gradle.api.Project;
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Project project, Provider<String> string) {
                        string.map(stringValue -> stringValue + " yo");
                    }
                }
                """);
        }

        @Test
        void provider_factory() {
            // language=Java
            refactorFromTo(
                    """
                import org.gradle.api.provider.Provider;
                import org.gradle.api.provider.ProviderFactory;

                class Test {
                    void test(ProviderFactory providerFactory, Provider<String> provider) {
                        providerFactory.provider(() -> provider.get() + " yo");
                    }
                }
                """,
                    """
                import org.gradle.api.provider.Provider;
                import org.gradle.api.provider.ProviderFactory;

                class Test {
                    void test(ProviderFactory providerFactory, Provider<String> provider) {
                        provider.map(providerInner -> providerInner + " yo");
                    }
                }
                """);
        }

        @Test
        void does_not_change_unrelated_non_provider_factory_method() {
            // language=Java
            expectUnchanged(
                    """
                import com.google.common.base.Suppliers;
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Provider<String> provider) {
                        // BUG: Diagnostic contains: Provider.get
                        Suppliers.memoize(() -> provider.get() + " yo");
                    }
                }
                """);
        }

        @Test
        void braces_in_lambda() {
            // language=Java
            refactorFromTo(
                    """
                import org.gradle.api.Project;
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Project project, Provider<String> provider) {
                        project.provider(() -> {
                            String tempVar = provider.get() + " blah";
                            return tempVar + " yo";
                        });
                    }
                }
                """,
                    """
                import org.gradle.api.Project;
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Project project, Provider<String> provider) {
                        provider.map(providerValue -> {
                            String tempVar = providerValue + " blah";
                            return tempVar + " yo";
                        });
                    }
                }
                """);
        }

        @Test
        void multiple_providers_it_will_only_change_the_first_one() {
            // language=Java
            refactorFromTo(
                    """
                import org.gradle.api.Project;
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Project project, Provider<Integer> first, Provider<String> second) {
                        project.provider(() -> first.get() + second.get());
                    }
                }
                """,
                    """
                import org.gradle.api.Project;
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Project project, Provider<Integer> first, Provider<String> second) {
                        // BUG: Diagnostic contains: Provider.get
                        first.map(firstValue -> firstValue + second.get());
                    }
                }
                """);
        }

        @Test
        void complex_expression_to_get_provider() {
            // language=Java
            refactorFromTo(
                    """
                import java.util.List;
                import org.gradle.api.Project;
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Project project, List<Provider<Integer>> providers) {
                        project.provider(() -> providers.get(0).get() + "hi");
                    }
                }
                """,
                    """
                import java.util.List;
                import org.gradle.api.Project;
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Project project, List<Provider<Integer>> providers) {
                        providers.get(0).map(integer -> integer + "hi");
                    }
                }
                """);
        }

        @Test
        void doesnt_lift_get_through_multiple_lambdas() {
            // language=Java
            expectUnchanged(
                    """
                import java.util.Optional;
                import org.gradle.api.Project;
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Project project, Provider<Integer> provider) {
                        // BUG: Diagnostic contains: Provider.get
                        project.provider(() -> Optional.of(3).map(value -> provider.get() + value));
                    }
                }
                """);
        }

        @Test
        void identity_mapping() {
            // language=Java
            refactorFromTo(
                    """
                import org.gradle.api.Project;
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Project project, Provider<String> provider) {
                        System.out.println(project.provider(() -> provider.get()));
                    }
                }
                """,
                    """
                import org.gradle.api.Project;
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Project project, Provider<String> provider) {
                        System.out.println(provider);
                    }
                }
                """);
        }
    }

    private void refactorFromTo(String input, String output) {
        bestEffortRefactoringValidator()
                .addInputLines("Test.java", input)
                .addOutputLines("Test.java", output)
                .doTest();
    }

    private void expectUnchanged(String input) {
        bestEffortRefactoringValidator()
                .addInputLines("Test.java", input)
                .expectUnchanged()
                .doTest();
    }

    private RefactoringValidator bestEffortRefactoringValidator() {
        return refactoringValidator("-XepOpt:GradleGuide:BestEffortMode");
    }

    private RefactoringValidator refactoringValidator(String... args) {
        return RefactoringValidator.of(AvoidEagerlyEvaluatingProviders.class, getClass(), args);
    }
}
