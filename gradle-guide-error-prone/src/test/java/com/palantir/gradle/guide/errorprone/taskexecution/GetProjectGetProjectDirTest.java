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

public class GetProjectGetProjectDirTest extends IllegalMethodCalledDuringTaskExecutionTest {
    @Test
    void projectLayout_not_injected_should_fix_and_inject() {
        testFix(
                "AlreadyAbstractTask.java",
                """
                import java.io.File;
                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                import org.gradle.api.tasks.TaskAction;

                abstract class AlreadyAbstractTask extends DefaultTask {
                    @TaskAction
                    final void action() {
                        File projectDir = getProject().getProjectDir();
                    }
                }
            """,
                """
                import java.io.File;
                import javax.inject.Inject;
                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                import org.gradle.api.file.ProjectLayout;
                import org.gradle.api.tasks.TaskAction;

                abstract class AlreadyAbstractTask extends DefaultTask {
                    @Inject
                    protected abstract ProjectLayout getProjectLayout();

                    @TaskAction
                    final void action() {
                        File projectDir = getProjectLayout().getProjectDirectory().getAsFile();
                    }
                }
            """);
    }

    @Test
    void projectLayout_already_injected_should_fix_without_injecting_again() {
        testFix(
                "AlreadyInjectedTask.java",
                """
                import java.io.File;
                import javax.inject.Inject;
                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                import org.gradle.api.file.ProjectLayout;
                import org.gradle.api.tasks.TaskAction;

                abstract class AlreadyInjectedTask extends DefaultTask {
                    private static final String IM_AT_THE_TOP_OF_THE_CLASS = "happy_squirrel.txt";

                    @Inject
                    protected abstract ProjectLayout getProjectLayout();

                    @TaskAction
                    final void action() {
                        getProjectLayout().getSettingsDirectory().file(IM_AT_THE_TOP_OF_THE_CLASS);
                        File projectDir = getProject().getProjectDir();
                    }
                }
            """,
                """
                import java.io.File;
                import javax.inject.Inject;
                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                import org.gradle.api.file.ProjectLayout;
                import org.gradle.api.tasks.TaskAction;

                abstract class AlreadyInjectedTask extends DefaultTask {
                    private static final String IM_AT_THE_TOP_OF_THE_CLASS = "happy_squirrel.txt";

                    @Inject
                    protected abstract ProjectLayout getProjectLayout();

                    @TaskAction
                    final void action() {
                        getProjectLayout().getSettingsDirectory().file(IM_AT_THE_TOP_OF_THE_CLASS);
                        File projectDir = getProjectLayout().getProjectDirectory().getAsFile();
                    }
                }
            """);
    }

    @Test
    void concrete_task_should_fix() {
        testFix(
                "CustomTask.java",
                """
                import java.io.File;
                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                import org.gradle.api.tasks.TaskAction;

                class CustomTask extends DefaultTask {
                    @TaskAction
                    final void action() {
                        File projectDir = getProject().getProjectDir();
                    }
                }
            """,
                """
                import java.io.File;
                import javax.inject.Inject;
                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                import org.gradle.api.file.ProjectLayout;
                import org.gradle.api.tasks.TaskAction;

                abstract class CustomTask extends DefaultTask {
                    @Inject
                    protected abstract ProjectLayout getProjectLayout();

                    @TaskAction
                    final void action() {
                        File projectDir = getProjectLayout().getProjectDirectory().getAsFile();
                    }
                }
            """);
    }
}