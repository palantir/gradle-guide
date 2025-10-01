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

public class GetProjectTest extends IllegalMethodCalledDuringTaskExecutionTest {
    @Test
    void getProject_within_TaskAction_should_fail() {
        test("""
                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                import org.gradle.api.tasks.TaskAction;

                abstract class CustomTask extends DefaultTask {
                    @TaskAction
                    final void action() {
                        // BUG: Diagnostic contains: Don't call `getProject()` in task actions
                        String projectName = getProject().getName();

                        // BUG: Diagnostic contains: Don't call `getProject()` in task actions
                        String projectPath = getProject().getPath();
                    }
                }
            """);
    }

    @Test
    void transitive_calls_to_getProject_should_fail() {
        test("""
                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                import org.gradle.api.tasks.TaskAction;

                abstract class CustomTask extends DefaultTask {
                    @TaskAction
                    final void action() {
                        // BUG: Diagnostic contains: Don't call `getProject()` in task actions
                        String projectName = getProject().getName();

                        helper();
                    }

                    void helper() {
                        // BUG: Diagnostic contains: Don't call `getProject()` in task actions
                        String projectPath = getProject().getPath();
                    }
                }
            """);
    }

    @Test
    void transitive_calls_to_getProject_should_fail_in_taskActions() {
        test("""
            import org.gradle.api.Action;
            import org.gradle.api.Task;

            abstract class CustomTaskAction implements Action<Task> {
                @Override
                public void execute(Task task) {
                    // BUG: Diagnostic contains: Don't call `getProject()` in task actions
                    String projectName = task.getProject().getName();
                }
            }
            """);
    }

    @Test
    void getProject_outside_of_TaskAction_should_pass() {
        test("""
            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.tasks.TaskAction;

            abstract class CustomTask extends DefaultTask {
                private String projectName;

                public final void not_called_anywhere() {
                    getProject();
                }

                @TaskAction
                public final void action() {
                    System.out.println("Project name: " + projectName);
                }
            }
            """);
    }

    @Test
    void getProject_in_constructors_should_pass() {
        test("""
            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.tasks.TaskAction;

            abstract class CustomTask extends DefaultTask {
                private String projectName;

                CustomTask() {
                    projectName = getProject().getName();
                    called_in_constructor();
                }

                public final void called_in_constructor() {
                    getProject();
                }

                @TaskAction
                public final void action() {
                    System.out.println("Project name: " + projectName);
                }
            }
            """);
    }

    @Test
    void getProject_in_shared_helper_should_fail() {
        test("""
            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.tasks.TaskAction;

            abstract class CustomTask extends DefaultTask {
                private String projectName;

                CustomTask() {
                    getProjectName();
                }

                public final String getProjectName() {
                    // BUG: Diagnostic contains: Don't call `getProject()` in task actions
                    return getProject().getName();
                }

                @TaskAction
                public final void action() {
                    System.out.println("Project name: " + getProjectName());
                }
            }
            """);
    }

    @Test
    void suppressed_calls_to_getProject_should_pass() {
        test("""
                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                import org.gradle.api.tasks.TaskAction;

                abstract class CustomTask extends DefaultTask {
                    @TaskAction
                    final void action() {
                        @SuppressWarnings("IllegalMethodCalledDuringTaskExecution")
                        String projectName = getProject().getName();

                        suppressed_statement();
                        suppressed_method();
                        no_oversuppression();
                    }

                    void suppressed_statement() {
                        @SuppressWarnings("IllegalMethodCalledDuringTaskExecution")
                        String projectPath = getProject().getPath();
                    }

                    @SuppressWarnings("IllegalMethodCalledDuringTaskExecution")
                    void suppressed_method() {
                        String projectPath = getProject().getPath();
                    }

                    @SuppressWarnings("IllegalMethodCalledDuringTaskExecution")
                    void no_oversuppression() {
                        helper();
                    }

                    void helper() {
                        // BUG: Diagnostic contains: Don't call `getProject()` in task actions
                        String projectPath = getProject().getPath();
                    }
                }
            """);
    }

    @Test
    void getProject_in_TaskAction_doesnt_cause_other_getProjects_to_be_flagged() {
        test("""
                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                import org.gradle.api.tasks.TaskAction;

                abstract class CustomTask extends DefaultTask {
                    // getProject() in field is OK
                    private final Project project = getProject();

                    CustomTask() {
                        // getProject() in task constructor is OK
                        Project project = getProject();
                    }

                    @TaskAction
                    final void action() {
                        // ... even if getProject()
                        @SuppressWarnings("IllegalMethodCalledDuringTaskExecution")
                        Project project = getProject();
                    }
                }
            """);
    }

    @Test
    void action_without_calls_to_getProject_should_pass() {
        test("""
            import org.gradle.api.Action;
            import org.gradle.api.Task;
            import org.gradle.api.provider.Provider;

            abstract class CustomTaskAction implements Action<Task> {
                Provider<String> projectName;

                public CustomTaskAction(Provider<String> projectName) {
                    this.projectName = projectName;
                }

                @Override
                public void execute(Task task) {
                    System.out.println("Project name: " + projectName.get());
                }
            }
            """);
    }

    @Test
    void dont_fail_actions_of_nontask() {
        test("""
            import org.gradle.api.Action;
            import org.gradle.api.Task;

            abstract class CustomAction implements Action<Object> {
                @Override
                public void execute(Object obj) {
                    if (obj instanceof Task) {
                        // This should NOT be flagged, because this is not Action<Task>
                        String projectName = ((Task) obj).getProject().getName();
                    }
                }
            }
            """);
    }
}
