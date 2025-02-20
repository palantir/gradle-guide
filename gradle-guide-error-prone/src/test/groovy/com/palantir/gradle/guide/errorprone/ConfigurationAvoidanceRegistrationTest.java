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
import org.junit.jupiter.api.Test;

class ConfigurationAvoidanceRegistrationTest {
    private final CompilationTestHelper compilationTestHelper =
            CompilationTestHelper.newInstance(ConfigurationAvoidanceRegistration.class, getClass());

    @Test
    void matches_tasks_create() {
        compilationTestHelper.addSourceLines(
                "Test.java",
                // language=java
                """
            import groovy.lang.Closure;
            import java.util.Map;
            import org.gradle.api.Task;
            import org.gradle.api.tasks.TaskContainer;

            class Test {
                static void test(TaskContainer tasks) {
                    // BUG: Diagnostic contains: use `.register`
                    tasks.create(Map.of("name", "lol", "type", Task.class));
                    // BUG: Diagnostic contains: use `.register`
                    tasks.create(Map.of("name", "lol", "type", Task.class), Closure.IDENTITY);
                    // BUG: Diagnostic contains: use `.register`
                    tasks.create("lol", Closure.IDENTITY);
                    // BUG: Diagnostic contains: use `.register`
                    tasks.create("lol");
                    // BUG: Diagnostic contains: use `.register`
                    tasks.create("lol", Task.class);
                    // BUG: Diagnostic contains: use `.register`
                    tasks.create("lol", Task.class, new Object());
                    // BUG: Diagnostic contains: use `.register`
                    tasks.create("lol", Task.class, task -> {});
                }
            }
            """);

        compilationTestHelper.doTest();
    }

    @Test
    void matches_configurations_create() {
        compilationTestHelper.addSourceLines(
                "Test.java",
                // language=java
                """
            import groovy.lang.Closure;
            import org.gradle.api.artifacts.ConfigurationContainer;

            class Test {
                static void test(ConfigurationContainer configurations) {
                    // BUG: Diagnostic contains: use `.register`
                    configurations.create("lol");
                    // BUG: Diagnostic contains: use `.register`
                    configurations.create("lol", Closure.IDENTITY);
                    // BUG: Diagnostic contains: use `.register`
                    configurations.create("lol", conf -> {});
                }
            }
            """);

        compilationTestHelper.doTest();
    }
}
