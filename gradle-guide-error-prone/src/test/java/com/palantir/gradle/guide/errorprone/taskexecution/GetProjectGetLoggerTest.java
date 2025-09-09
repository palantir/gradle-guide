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

public class GetProjectGetLoggerTest extends IllegalMethodCalledDuringTaskExecutionTest {
    @Test
    void should_fix_getProject_getLogger() {
        testFix(
                "CustomTask.java",
                """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.logging.Logger;

            abstract class CustomTask extends DefaultTask {
                @TaskAction
                final void action() {
                    Logger logger = getProject().getLogger();
                }
            }
        """,
                """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.logging.Logger;

            abstract class CustomTask extends DefaultTask {
                @TaskAction
                final void action() {
                    Logger logger = getLogger();
                }
            }
        """);
    }

    @Test
    void fixes_whatever_it_can_and_warns_for_the_others() {
        testFix(
                "CustomTask.java",
                """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.logging.Logger;

            abstract class CustomTask extends DefaultTask {
                @TaskAction
                final void action() {
                    getProject();  // not fixable
                    Logger logger = getProject().getLogger();  // fixable to getLogger()
                }
            }
        """,
                """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.logging.Logger;

            abstract class CustomTask extends DefaultTask {
                @TaskAction
                final void action() {
                    // BUG: Diagnostic contains: Don't call `getProject()` in task actions
                    getProject();
                    Logger logger = getLogger();
                }
            }
            """);
    }

    @Test
    void should_fix_transitive_calls_to_getLogger() {
        testFix(
                "CustomTask.java",
                """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.logging.Logger;

            abstract class CustomTask extends DefaultTask {
                @TaskAction
                final void action() {
                    helper();
                }

                void helper() {
                    Logger logger = getProject().getLogger();  // fixable to getLogger()
                }
            }
        """,
                """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.logging.Logger;

            abstract class CustomTask extends DefaultTask {
                @TaskAction
                final void action() {
                    helper();
                }

                void helper() {
                    Logger logger = getLogger();
                }
            }
        """);
    }

    @Test
    void should_fix_getProject_getLogger_in_Action() {
        testFix(
                "CustomTaskAction.java",
                """
            import org.gradle.api.Action;
            import org.gradle.api.Task;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.logging.Logger;

            abstract class CustomTaskAction implements Action<Task> {
                @Override
                public void execute(Task task) {
                    Logger logger = task.getProject().getLogger();  // fixable to getLogger()
                }
            }
        """,
                """
            import org.gradle.api.Action;
            import org.gradle.api.Task;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.logging.Logger;

            abstract class CustomTaskAction implements Action<Task> {
                @Override
                public void execute(Task task) {
                    Logger logger = task.getLogger();
                }
            }
        """);
    }

    @Test
    void should_fix_getProject_getLogger_within_doFirst_doLast_blocks() {
        testFix(
                "MyPlugin.java",
                """
            import org.gradle.api.Action;
            import org.gradle.api.Project;
            import org.gradle.api.Plugin;
            import org.gradle.api.Task;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.logging.Logger;

            class MyPlugin implements Plugin<Project> {
                @Override
                public final void apply(Project project) {
                    // Should fix anonymous class Action<Task>
                    project.getTasks().withType(Task.class, tsk -> {
                        tsk.doFirst(new Action<Task>() {
                            @Override
                            public void execute(Task task) {
                                task.getProject().getLogger().info("I am a happy squirrel");
                            }
                        });
                    });

                    // TODO(okelvin): fix lambdas
                    project.getTasks().withType(Task.class, tsk -> tsk.doLast(
                        (Action<Task>) task -> task.getProject().getLogger().info("I am a happy squirrel")));
                }
            }
        """,
                """
            import org.gradle.api.Action;
            import org.gradle.api.Project;
            import org.gradle.api.Plugin;
            import org.gradle.api.Task;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.api.logging.Logger;

            class MyPlugin implements Plugin<Project> {
                @Override
                public final void apply(Project project) {
                    // Should fix anonymous class Action<Task>
                    project.getTasks().withType(Task.class, tsk -> {
                        tsk.doFirst(new Action<Task>() {
                            @Override
                            public void execute(Task task) {
                                task.getLogger().info("I am a happy squirrel");
                            }
                        });
                    });

                    // TODO(okelvin): fix lambdas
                    project.getTasks().withType(Task.class, tsk -> tsk.doLast(
                        (Action<Task>) task -> task.getProject().getLogger().info("I am a happy squirrel")));
                }
            }
        """);
    }
}