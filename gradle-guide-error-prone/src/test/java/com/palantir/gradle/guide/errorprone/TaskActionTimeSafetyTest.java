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

class TaskActionTimeSafetyTest {
    private final CompilationTestHelper compilationTestHelper =
            CompilationTestHelper.newInstance(TaskActionTimeSafety.class, getClass());

    @Test
    void provider_get_is_unsafe_in_plugin_apply() {
        compilationTestHelper
                .addSourceLines(
                        "SomePlugin.java",
                        // language=Java
                        """
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.Provider;

            class SomePlugin implements Plugin<Project> {
                public void apply(Project project) {
                    Provider<Integer> provider = project.provider(() -> 4);
                    // BUG: Diagnostic contains: .get
                    provider.get();
                }
            }
            """)
                .doTest();
    }

    @Test
    void provider_get_is_safe_inside_a_task_action() {
        compilationTestHelper
                .addSourceLines(
                        "SomePlugin.java",
                        // language=Java
                        """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.provider.Property;
            import org.gradle.api.provider.Provider;
            import org.gradle.api.tasks.Input;
            import org.gradle.api.tasks.TaskAction;

            abstract class SomeTask extends DefaultTask {
                @Input
                public abstract Property<String> getItem();

                @TaskAction
                public final void action() {
                    getItem().get();
                }
            }
            """)
                .doTest();
    }

    @Test
    void provider_get_is_safe_inside_a_private_method_in_the_same_compilation_unit_only_called_from_a_task_action() {
        compilationTestHelper
                .addSourceLines(
                        "SomePlugin.java",
                        // language=Java
                        """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.provider.Property;
            import org.gradle.api.provider.Provider;
            import org.gradle.api.tasks.Input;
            import org.gradle.api.tasks.TaskAction;

            abstract class SomeTask extends DefaultTask {
                @Input
                public abstract Property<String> getItem();

                @TaskAction
                public final void action() {
                    otherMethod();
                }

                private void otherMethod() {
                    getItem().get();
                }
            }
            """)
                .doTest();
    }

    @Test
    void provider_get_safe_private_method_in_same_compilation_unit_only_called_transitively_from_a_task_action() {
        compilationTestHelper
                .addSourceLines(
                        "SomePlugin.java",
                        // language=Java
                        """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.provider.Property;
            import org.gradle.api.provider.Provider;
            import org.gradle.api.tasks.Input;
            import org.gradle.api.tasks.TaskAction;

            abstract class SomeTask extends DefaultTask {
                @Input
                public abstract Property<String> getItem();

                @TaskAction
                public final void action() {
                    otherMethod();
                }

                private void otherMethod() {
                    anotherMethod();
                }

                private void anotherMethod() {
                    getItem().get();
                }
            }
            """)
                .doTest();
    }

    @Test
    void provider_get_unsafe_private_method_same_compilation_unit_called_from_constructor_and_task_action() {
        compilationTestHelper
                .addSourceLines(
                        "SomePlugin.java",
                        // language=Java
                        """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.provider.Property;
            import org.gradle.api.provider.Provider;
            import org.gradle.api.tasks.Input;
            import org.gradle.api.tasks.TaskAction;

            abstract class SomeTask extends DefaultTask {
                @Input
                public abstract Property<String> getItem();

                public SomeTask() {
                    otherMethod();
                }

                @TaskAction
                public final void action() {
                    otherMethod();
                }

                private void otherMethod() {
                    // BUG: Diagnostic contains: .get
                    getItem().get();
                }
            }
            """)
                .doTest();
    }
}
