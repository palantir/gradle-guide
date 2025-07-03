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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SuppressWarnings("MisformattedTestData")
class GetProjectInvocationsTest {
    private final CompilationTestHelper compilationTestHelper =
            CompilationTestHelper.newInstance(GetProjectInvocations.class, getClass());

    /**
     * Tests for {@code @TaskAction}.
     */
    @Nested
    class Tasks {
        @Test
        void getProjectWithinTaskActionsShouldFail() {
            test(
                    """
                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                import org.gradle.api.tasks.TaskAction;

                abstract class CustomTask extends DefaultTask {
                    @TaskAction
                    final void action() {
                        // BUG: Diagnostic contains: Don't call `getProject()` in task actions
                        String projectName = getProject().getName();
                        System.out.println("Project name: " + projectName);

                        // BUG: Diagnostic contains: Don't call `getProject()` in task actions
                        String projectPath = getProject().getPath();
                        System.out.println("Project path: " + projectPath);
                    }
                }
            """);
        }

        @Test
        void getProjectWithoutTaskActionsShouldPass() {
            test(
                    """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.tasks.TaskAction;

            abstract class CustomTask extends DefaultTask {
                private String projectName;

                CustomTask() {
                    projectName = getProject().getName();
                }

                @TaskAction
                public final void action() {
                    System.out.println("Project name: " + projectName);
                }
            }
            """);
        }
    }

    /**
     * Tests for overrides of {@code public void execute(Task)} in {@code Action<Task>}.
     * Note that this way of defining task actions is deprecated, and should NOT be used!
     */
    @Nested
    class Actions {
        @Test
        void getProjectWithinActionOfTaskShouldFail() {
            test(
                    """
            import org.gradle.api.Action;
            import org.gradle.api.Task;

            abstract class CustomTaskAction implements Action<Task> {
                @Override
                public void execute(Task task) {
                    // BUG: Diagnostic contains: Don't call `getProject()` in task actions
                    String projectName = task.getProject().getName();
                    System.out.println("Project name: " + projectName);
                }
            }
            """);
        }

        @Test
        void actionOfTaskShouldPass() {
            test(
                    """
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
        void getProjectWithinActionOfNonTaskShouldPass() {
            test(
                    """
            import org.gradle.api.Action;
            import org.gradle.api.Task;

            abstract class CustomAction implements Action<Object> {
                @Override
                public void execute(Object obj) {
                    if (obj instanceof Task) {
                        // This should NOT be flagged, because this is not Action<Task>
                        String projectName = ((Task) obj).getProject().getName();
                        System.out.println("Project name: " + projectName);
                    }
                }
            }
            """);
        }
    }

    private void test(@Language("Java") String source) {
        compilationTestHelper.addSourceLines("Test.java", source).doTest();
    }
}
