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

@SuppressWarnings("MisformattedTestData")
class UseGradleExecInsteadOfProviderFactoryExecTest {
    private final CompilationTestHelper compilationTestHelper =
            CompilationTestHelper.newInstance(UseGradleExecInsteadOfProviderFactoryExec.class, getClass());

    @Test
    void matches_provider_factory_exec() {
        compilationTestHelper.addSourceLines(
                "Test.java",
                // language=java
                """
                import org.gradle.api.DefaultTask;
                import org.gradle.api.provider.ProviderFactory;
                import javax.inject.Inject;

                abstract class Test extends DefaultTask {
                    @Inject
                    abstract ProviderFactory getProviderFactory();

                    void doSomething() {
                        // BUG: Diagnostic contains: Use GradleExec.exec() instead
                        getProviderFactory().exec(spec -> {
                            spec.commandLine("echo", "hello");
                        });

                        // BUG: Diagnostic contains: Use GradleExec.exec() instead
                        getProject().getProviders().exec(spec -> {
                            spec.commandLine("ls");
                        });
                    }
                }
                """);

        compilationTestHelper.doTest();
    }

    @Test
    void does_not_match_other_exec_methods() {
        compilationTestHelper.addSourceLines(
                "Test.java",
                // language=java
                """
                import org.gradle.api.DefaultTask;
                import org.gradle.process.ExecOperations;
                import javax.inject.Inject;

                abstract class Test extends DefaultTask {
                    @Inject
                    abstract ExecOperations getExecOperations();

                    @Inject
                    abstract org.gradle.api.provider.ProviderFactory getProviderFactory();

                    void doSomething() {
                        // Should NOT be flagged - ExecOperations.exec is fine
                        getExecOperations().exec(spec -> {
                            spec.commandLine("echo", "hello");
                        });

                        // Should NOT be flagged - other ProviderFactory methods
                        getProviderFactory().provider(() -> "hello");
                        getProviderFactory().environmentVariable("PATH");
                    }
                }
                """);

        compilationTestHelper.expectNoDiagnostics().doTest();
    }

    @Test
    void suppressed_calls_should_pass() {
        compilationTestHelper.addSourceLines(
                "Test.java",
                // language=java
                """
                import org.gradle.api.DefaultTask;
                import org.gradle.api.provider.ProviderFactory;
                import javax.inject.Inject;

                abstract class Test extends DefaultTask {
                    @Inject
                    abstract ProviderFactory getProviderFactory();

                    @SuppressWarnings("UseGradleExecInsteadOfProviderFactoryExec")
                    void doSomething() {
                        getProviderFactory().exec(spec -> {
                            spec.commandLine("echo", "suppressed");
                        });
                    }
                }
                """);

        compilationTestHelper.expectNoDiagnostics().doTest();
    }
}