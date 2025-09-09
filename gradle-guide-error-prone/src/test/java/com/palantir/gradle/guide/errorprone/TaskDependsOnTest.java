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

    @Test
    void should_fix_getProject_getLogger() {
        test(
                """
            import java.io.File;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.DefaultTask;
            import org.gradle.api.file.RegularFile;
            import org.gradle.api.file.RegularFileProperty;
            import org.gradle.api.provider.Provider;
            import org.gradle.api.tasks.Input;
            import org.gradle.api.tasks.InputFile;
            import org.gradle.api.tasks.OutputFile;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.tasks.TaskProvider;

            abstract class MyPlugin implements Plugin<Project> {

                abstract class ProducingTask extends DefaultTask {
                    @OutputFile
                    public abstract RegularFileProperty getOutputFile();
                }

                abstract class ConsumingTask extends DefaultTask {
                    @InputFile
                    public abstract RegularFileProperty getInputFile();
                }

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

                }
            }
        """);
    }

    private void test(@Language("Java") String source) {
        compilationTestHelper.addSourceLines("Test.java", source).doTest();
    }
}
