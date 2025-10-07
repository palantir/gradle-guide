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
public class ArchiveOperationsFixesTest extends IllegalMethodCalledDuringTaskExecutionTest {
    @Test
    void archiveOperations_not_injected_should_fix_and_inject() {
        testFix(
                "AlreadyAbstractTask.java",
                """
                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                import org.gradle.api.file.FileTree;
                import org.gradle.api.tasks.TaskAction;
                abstract class AlreadyAbstractTask extends DefaultTask {
                    @TaskAction
                    final void action() {
                        FileTree fileTree = getProject().tarTree("happy-squirrel.tar");
                    }
                }
                """,
                """
                import javax.inject.Inject;
                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                import org.gradle.api.file.ArchiveOperations;
                import org.gradle.api.file.FileTree;
                import org.gradle.api.tasks.TaskAction;
                abstract class AlreadyAbstractTask extends DefaultTask {
                    @Inject
                    protected abstract ArchiveOperations getArchiveOperations();
                    @TaskAction
                    final void action() {
                        FileTree fileTree = getArchiveOperations().tarTree("happy-squirrel.tar");
                    }
                }
                """);
    }

    @Test
    void archiveOperations_already_injected_should_fix_without_injecting_again() {
        testFix(
                "AlreadyInjectedTask.java",
                """
                import java.io.File;
                import javax.inject.Inject;
                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                import org.gradle.api.file.ArchiveOperations;
                import org.gradle.api.file.FileTree;
                import org.gradle.api.tasks.TaskAction;
                abstract class AlreadyInjectedTask extends DefaultTask {
                    private static final String IM_AT_THE_TOP_OF_THE_CLASS = "happy_squirrel.txt";
                    @Inject
                    protected abstract ArchiveOperations getArchiveOperations();
                    @TaskAction
                    final void action() {
                        FileTree fileTree = getProject().tarTree("happy-squirrel.tar");
                    }
                }
                """,
                """
                import java.io.File;
                import javax.inject.Inject;
                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                import org.gradle.api.file.ArchiveOperations;
                import org.gradle.api.file.FileTree;
                import org.gradle.api.tasks.TaskAction;
                abstract class AlreadyInjectedTask extends DefaultTask {
                    private static final String IM_AT_THE_TOP_OF_THE_CLASS = "happy_squirrel.txt";
                    @Inject
                    protected abstract ArchiveOperations getArchiveOperations();
                    @TaskAction
                    final void action() {
                        FileTree fileTree = getArchiveOperations().tarTree("happy-squirrel.tar");
                    }
                }
                """);

        testFix(
                "AlreadyInjectedTask.java",
                """
                import java.io.File;
                import javax.inject.Inject;
                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                import org.gradle.api.file.ArchiveOperations;
                import org.gradle.api.file.FileTree;
                import org.gradle.api.tasks.TaskAction;
                abstract class AlreadyInjectedTask extends DefaultTask {
                    private static final String IM_AT_THE_TOP_OF_THE_CLASS = "happy_squirrel.txt";
                    private final ArchiveOperations archiveOps;

                    AlreadyInjectedTask(ArchiveOperations archiveOps) {
                        this.archiveOps = archiveOps;
                    }

                    @TaskAction
                    final void action() {
                        FileTree fileTree = getProject().tarTree("happy-squirrel.tar");
                    }
                }
                """,
                """
                import java.io.File;
                import javax.inject.Inject;
                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                import org.gradle.api.file.ArchiveOperations;
                import org.gradle.api.file.FileTree;
                import org.gradle.api.tasks.TaskAction;
                abstract class AlreadyInjectedTask extends DefaultTask {
                    private static final String IM_AT_THE_TOP_OF_THE_CLASS = "happy_squirrel.txt";
                    private final ArchiveOperations archiveOps;

                    AlreadyInjectedTask(ArchiveOperations archiveOps) {
                        this.archiveOps = archiveOps;
                    }

                    @TaskAction
                    final void action() {
                        FileTree fileTree = archiveOps.tarTree("happy-squirrel.tar");
                    }
                }
                """);

        testFix(
                "AlreadyInjectedTask.java",
                """
                import java.io.File;
                import javax.inject.Inject;
                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                import org.gradle.api.file.ArchiveOperations;
                import org.gradle.api.file.FileTree;
                import org.gradle.api.tasks.TaskAction;
                abstract class AlreadyInjectedTask extends DefaultTask {
                    private static final String IM_AT_THE_TOP_OF_THE_CLASS = "happy_squirrel.txt";
                    @Inject
                    protected abstract ArchiveOperations getArchiveOps();
                    @TaskAction
                    final void action() {
                        FileTree fileTree = getProject().tarTree("happy-squirrel.tar");
                    }
                }
                """,
                """
                import java.io.File;
                import javax.inject.Inject;
                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                import org.gradle.api.file.ArchiveOperations;
                import org.gradle.api.file.FileTree;
                import org.gradle.api.tasks.TaskAction;
                abstract class AlreadyInjectedTask extends DefaultTask {
                    private static final String IM_AT_THE_TOP_OF_THE_CLASS = "happy_squirrel.txt";
                    @Inject
                    protected abstract ArchiveOperations getArchiveOps();
                    @TaskAction
                    final void action() {
                        FileTree fileTree = getArchiveOps().tarTree("happy-squirrel.tar");
                    }
                }
                """);
    }

    @Test
    void concrete_task_should_fix() {
        testFix(
                "ConcreteTask.java",
                """
                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                import org.gradle.api.file.FileTree;
                import org.gradle.api.tasks.TaskAction;
                class ConcreteTask extends DefaultTask {
                    @TaskAction
                    final void action() {
                        FileTree fileTree = getProject().tarTree("happy-squirrel.tar");
                    }
                }
                """,
                """
                import javax.inject.Inject;
                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                import org.gradle.api.file.ArchiveOperations;
                import org.gradle.api.file.FileTree;
                import org.gradle.api.tasks.TaskAction;
                abstract class ConcreteTask extends DefaultTask {
                    @Inject
                    protected abstract ArchiveOperations getArchiveOperations();
                    @TaskAction
                    final void action() {
                        FileTree fileTree = getArchiveOperations().tarTree("happy-squirrel.tar");
                    }
                }
                """);
    }

    @Test
    void transitive_calls_to_getProject_copy_should_fail_in_taskActions() {
        test(
                """
                import org.gradle.api.Action;
                import org.gradle.api.Task;
                abstract class CustomTaskAction implements Action<Task> {
                    @Override
                    public void execute(Task task) {
                        // BUG: Diagnostic contains: Instead of `getProject().tarTree(...)`, do `getArchiveOperations().tarTree(...)`
                        task.getProject().tarTree("happy-squirrel.tar");
                        bad_helper(task);
                    }
                    public void bad_helper(Task task) {
                        System.out.println("I am a happy squirrel");
                        // BUG: Diagnostic contains: Instead of `getProject().tarTree(...)`, do `getArchiveOperations().tarTree(...)`
                        task.getProject().tarTree("happy-squirrel.tar");
                    }
                }
                """);
    }
}
