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
        // language=JAVA
        private static final String badTask =
                """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.tasks.TaskAction;

            public abstract class CustomTask extends DefaultTask {
                @TaskAction
                public final void action() {
                    // BUG: Diagnostic contains: Don't call `getProject()` in task actions
                    String projectName = getProject().getName();
                    System.out.println("Project name: " + projectName);

                    // BUG: Diagnostic contains: Don't call `getProject()` in task actions
                    String projectPath = getProject().getPath();
                    System.out.println("Project path: " + projectPath);
                }
            }
            """;

        // language=JAVA
        private static final String goodTask =
                """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.tasks.TaskAction;

            public abstract class CustomTask extends DefaultTask {
                private String projectName;

                CustomTask() {
                    projectName = getProject().getName();
                }

                @TaskAction
                public final void action() {
                    System.out.println("Project name: " + projectName);
                }
            }
            """;

        @Test
        void getProjectWithinTaskActionsShouldFail() {
            compilationTestHelper.addSourceLines("CustomTask.java", badTask).doTest();
        }

        @Test
        void getProjectWithoutTaskActionsShouldPass() {
            compilationTestHelper.addSourceLines("CustomTask.java", goodTask).doTest();
        }
    }

    /**
     * Tests for overrides of {@code public void execute(Task)} in {@code Action<Task>}.
     * Note that this way of defining task actions is deprecated, and should NOT be used!
     */
    @Nested
    class Actions {
        // language=JAVA
        private static final String badActionOfTask =
                """
            import org.gradle.api.Action;
            import org.gradle.api.Task;

            public abstract class CustomTaskAction implements Action<Task> {
                @Override
                public void execute(Task task) {
                    // BUG: Diagnostic contains: Don't call `getProject()` in task actions
                    String projectName = task.getProject().getName();
                    System.out.println("Project name: " + projectName);
                }
            }
            """;

        // language=JAVA
        private static final String goodActionOfTask =
                """
            import org.gradle.api.Action;
            import org.gradle.api.Task;
            import org.gradle.api.provider.Provider;

            public abstract class CustomTaskAction implements Action<Task> {
                Provider<String> projectName;

                public CustomTaskAction(Provider<String> projectName) {
                    this.projectName = projectName;
                }

                @Override
                public void execute(Task task) {
                    System.out.println("Project name: " + projectName.get());
                }
            }
            """;

        // language=JAVA
        private static final String actionOfObject =
                """
            import org.gradle.api.Action;
            import org.gradle.api.Task;

            public abstract class CustomAction implements Action<Object> {
                @Override
                public void execute(Object obj) {
                    if (obj instanceof Task) {
                        // This should NOT be flagged, because this is not Action<Task>
                        String projectName = ((Task) obj).getProject().getName();
                        System.out.println("Project name: " + projectName);
                    }
                }
            }
            """;

        // language=JAVA
        private static final String myClassWithExecuteMethod =
                """
            import org.gradle.api.Action;
            import org.gradle.api.Task;

            public abstract class MyClass {
                public void execute(Task task) {
                    // This should NOT be flagged, because this is not Action<Task>
                    String projectName = task.getProject().getName();
                    System.out.println("Project name: " + projectName);
                }
            }
            """;

        @Test
        void getProjectWithinActionOfTaskShouldFail() {
            compilationTestHelper
                    .addSourceLines("CustomTaskAction.java", badActionOfTask)
                    .doTest();
        }

        @Test
        void actionOfTaskShouldPass() {
            compilationTestHelper
                    .addSourceLines("CustomTaskAction.java", goodActionOfTask)
                    .doTest();
        }

        @Test
        void getProjectWithinActionOfNonTaskShouldPass() {
            compilationTestHelper
                    .addSourceLines("CustomAction.java", actionOfObject)
                    .doTest();
        }

        @Test
        void getProjectWithinNonActionShouldPass() {
            compilationTestHelper
                    .addSourceLines("MyClass.java", myClassWithExecuteMethod)
                    .doTest();
        }
    }
}
