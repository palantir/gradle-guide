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

@SuppressWarnings("LineLength")
class TaskDependsOnTest {
    private final CompilationTestHelper compilationTestHelper =
            CompilationTestHelper.newInstance(TaskDependsOn.class, getClass());

    // language=Java
    private final String testSetup =
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
    void dependsOn_custom_task_subtype_should_fail() {
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
    void dependsOn_DefaultTask_should_pass() {
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

                        // These are OK, because we assume that a regular DefaultTask is a lifecycle task
                        lifecycleTask.configure(lifecycle -> lifecycle.dependsOn(task));
                        check.dependsOn(task);
                    });

                    TaskProvider<Task> consumingTask = project.getTasks().named("consumingTask");
                    consumingTask.configure(genericTask -> {
                        // Ideally we'd catch this case, but we don't know whether consumingTask is actually a custom task or not, so be conservative
                        genericTask.dependsOn(producingTask);
                    });
                }
            }
        """);
    }

    private void test(@Language("Java") String source) {
        compilationTestHelper.addSourceLines("Test.java", testSetup + source).doTest();
    }
}
