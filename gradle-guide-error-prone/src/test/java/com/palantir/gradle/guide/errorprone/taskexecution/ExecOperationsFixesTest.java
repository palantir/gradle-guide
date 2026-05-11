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

package com.palantir.gradle.guide.errorprone.taskexecution;

import org.junit.jupiter.api.Test;

@SuppressWarnings("LineLength")
public class ExecOperationsFixesTest extends IllegalMethodCalledDuringTaskExecutionTest {
    @Test
    void should_fix() {
        testFix("ConcreteTask.java", """
            import java.io.File;
            import java.util.List;
            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.tasks.TaskAction;
            class ConcreteTask extends DefaultTask {
                @TaskAction
                final void action() {
                    getProject().exec(execSpec -> {
                        execSpec.setCommandLine(List.of("cat", "happy-squirrel.txt"));
                    });
                }
            }
            """, """
            import java.io.File;
            import java.util.List;
            import javax.inject.Inject;
            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.process.ExecOperations;
            abstract class ConcreteTask extends DefaultTask {

                @Inject
                protected abstract ExecOperations getExecOperations();

                @TaskAction
                final void action() {
                    getExecOperations().exec(execSpec -> {
                        execSpec.setCommandLine(List.of("cat", "happy-squirrel.txt"));
                    });
                }
            }
            """);
    }
}
