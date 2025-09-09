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

class TaskDependsOnTest {
    private final CompilationTestHelper compilationTestHelper =
            CompilationTestHelper.newInstance(TaskDependsOn.class, getClass());

    // language=Java
    private final String TEST_SETUP =
            """
            import java.io.File;
            import org.gradle.api.*;
            import org.gradle.api.file.*;
            import org.gradle.api.provider.*;
            import org.gradle.api.tasks.*;
            import org.gradle.api.tasks.testing.*;
            import org.gradle.api.tasks.bundling.*;

            abstract class ProducingTask extends DefaultTask {
                @OutputFile
                public abstract RegularFileProperty getOutputFile();
            }

            abstract class ConsumingTask extends DefaultTask {
                @InputFile
                public abstract RegularFileProperty getInputFile();
            }
    """;

    @Test
    void dependsOn_subclass_of_task_should_fail() {
        test(
                """
            abstract class MyPlugin implements Plugin<Project> {
                @Override
                public final void apply(Project project) {
                    Provider<RegularFile> sharedFile = project.getLayout().getBuildDirectory().file("happy-squirrel.txt");

                    TaskProvider<ProducingTask> producingTask = project.getTasks().register("producingTask", ProducingTask.class, task -> {
                        task.getOutputFile().set(sharedFile);
                    });

                    TaskProvider<ConsumingTask> consumingTask = project.getTasks().register("consumingTask", ConsumingTask.class, task -> {
                        task.getInputFile().set(sharedFile);
                        // BUG: Diagnostic contains: Instead of `task1.dependsOn(task2)`, wire up the outputs of task2 to the inputs of task1 using providers
                        task.dependsOn(producingTask);
                    });

                    consumingTask.configure(task -> {
                        // BUG: Diagnostic contains: Instead of `task1.dependsOn(task2)`, wire up the outputs of task2 to the inputs of task1 using providers
                        task.dependsOn(producingTask);
                    });
                }
            }
        """);
    }

    @Test
    void dependsOn_generic_task_should_pass() {
        test(
                """
            abstract class MyPlugin implements Plugin<Project> {
                @Override
                public final void apply(Project project) {
                    Provider<RegularFile> sharedFile = project.getLayout().getBuildDirectory().file("happy-squirrel.txt");

                    TaskProvider<ProducingTask> producingTask = project.getTasks().register("producingTask", ProducingTask.class, task -> {
                        task.getOutputFile().set(sharedFile);
                    });

                    TaskProvider<?> lifecycleTask = project.getTasks().register("lifecycleTask");
                    Task check = project.getTasks().getByName("check");
                    project.getTasks().register("consumingTask", ConsumingTask.class, task -> {
                        task.getInputFile().set(sharedFile);

                        // These are OK, because we assume that all lifecycle tasks to be non-custom, regular `Task`s
                        lifecycleTask.configure(lifecycle -> lifecycle.dependsOn(task));
                        check.dependsOn(check);
                    });

                    TaskProvider<Task> consumingTask = project.getTasks().named("consumingTask");
                    consumingTask.configure(task -> {
                        // Ideally we'd catch this case, but we don't know whether producingTask is actually a custom task or not, so be conservative
                        task.dependsOn(producingTask);
                    });
                }
            }
        """);
    }

    @Test
    void dependsOn_known_lifecycle_task_should_pass() {
        test(
                """
            abstract class MyPlugin implements Plugin<Project> {
                @Override
                public final void apply(Project project) {
                    Provider<RegularFile> sharedFile = project.getLayout().getBuildDirectory().file("happy-squirrel.txt");

                    TaskProvider<Jar> jarTask = project.getTasks().named("jar", Jar.class);
                    TaskCollection<Test> testTasks = project.getTasks().withType(Test.class);

                    TaskProvider<ProducingTask> producingTask = project.getTasks().register("producingTask", ProducingTask.class, task -> {
                        task.getOutputFile().set(sharedFile);
                        // These are okay, as Jar and Test are lifecycle tasks
                        jarTask.configure(jar -> jar.dependsOn(task));
                        testTasks.configureEach(test -> test.dependsOn(task));
                        project.getTasks().named("check").configure(check -> check.dependsOn(task));
                    });
                }
            }
        """);
    }

    private void test(@Language("Java") String source) {
        compilationTestHelper.addSourceLines("Test.java", TEST_SETUP + source).doTest();
    }
}
