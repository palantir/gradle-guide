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

    @Nested
    class Tasks {
        // language=JAVA
        private String badTaskCode =
                """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.tasks.TaskAction;

            public abstract class PrintProjectName extends DefaultTask {
                @TaskAction
                // BUG: Diagnostic contains: Don't call getProject() in task actions
                public final void action() {
                    String projectName = getProject().getName();
                    System.out.println("Project name: " + projectName);
                }
            }
            """;

        // language=JAVA
        private String goodTaskCode =
                """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.tasks.TaskAction;

            public abstract class PrintProjectName extends DefaultTask {
                private String projectName;

                PrintProjectName() {
                    projectName = getProject().getName();
                }

                @TaskAction
                public final void action() {
                    System.out.println("Project name: " + projectName);
                }
            }
            """;

        @SuppressWarnings("MisformattedTestData")
        @Test
        void getProjectWithinTaskActionsShouldFail() {
            compilationTestHelper
                    .addSourceLines("PrintProjectName.java", badTaskCode)
                    .doTest();
        }

        @SuppressWarnings("MisformattedTestData")
        @Test
        void getProjectWithoutTaskActionsShouldPass() {
            compilationTestHelper
                    .addSourceLines("PrintProjectName.java", goodTaskCode)
                    .doTest();
        }
    }
}
