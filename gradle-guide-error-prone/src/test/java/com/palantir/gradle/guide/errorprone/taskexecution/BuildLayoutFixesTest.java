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
public class BuildLayoutFixesTest extends IllegalMethodCalledDuringTaskExecutionTest {
    @Test
    void transitive_calls_to_violations_should_fail_in_taskActions() {
        test("""
            import org.gradle.api.Action;
            import org.gradle.api.Task;
            import java.io.File;


            abstract class CustomTaskAction implements Action<Task> {
                @Override
                public void execute(Task task) {
                    // BUG: Diagnostic contains: Instead of `getProject().getRootDir()`, the task should take the root directory as a task input
                    File rootDir = task.getProject().getRootDir();
                    bad_helper(task);
                }

                public void bad_helper(Task task) {
                    System.out.println("I am a happy squirrel");
                    // BUG: Diagnostic contains: Instead of `getProject().getRootDir()`, the task should take the root directory as a task input
                    File rootDir = task.getProject().getRootDir();
                }
            }
            """);
    }
}
