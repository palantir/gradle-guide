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
import com.palantir.gradle.guide.helpers.RefactoringValidator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProviderGetTest {
    private final CompilationTestHelper compilationTestHelper =
            CompilationTestHelper.newInstance(ProviderGet.class, getClass());

    @Test
    void detects_provider_get_usage() {
        compilationTestHelper
                .addSourceLines(
                        "Test.java",
                        // language=Java
                        """
            import org.gradle.api.provider.Provider;

            class Test {
                void test(Provider<String> provider) {
                    // BUG: Diagnostic contains: Provider.get
                    provider.get();
                }
            }
            """)
                .doTest();
    }

    @Nested
    class GetInsideNewProvider {
        @Test
        void basic_case() {
            // language=Java
            refactorFromTo(
                    """
                import org.gradle.api.Project;
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Project project, Provider<String> provider) {
                        project.provider(() -> provider.get() + " yo");
                    }
                }
                """,
                    """
                import org.gradle.api.Project;
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Project project, Provider<String> provider) {
                        provider.map(providerValue -> providerValue + " yo");
                    }
                }
                """);
        }

        @Test
        void braces_in_lambda() {
            // language=Java
            refactorFromTo(
                    """
                import org.gradle.api.Project;
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Project project, Provider<String> provider) {
                        project.provider(() -> {
                            String tempVar = provider.get() + " blah";
                            return tempVar + " yo";
                        });
                    }
                }
                """,
                    """
                import org.gradle.api.Project;
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Project project, Provider<String> provider) {
                        provider.map(providerValue -> {
                            String tempVar = providerValue + " blah";
                            return tempVar + " yo";
                        });
                    }
                }
                """);
        }

        @Test
        void multiple_providers_it_will_only_change_the_first_one() {
            // language=Java
            refactorFromTo(
                    """
                import org.gradle.api.Project;
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Project project, Provider<Integer> first, Provider<String> second) {
                        project.provider(() -> first.get() + second.get());
                    }
                }
                """,
                    """
                import org.gradle.api.Project;
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Project project, Provider<Integer> first, Provider<String> second) {
                        // BUG: Diagnostic contains: Provider.get
                        first.map(firstValue -> firstValue + second.get());
                    }
                }
                """);
        }

        @Test
        void complex_expression_to_get_provider() {
            // language=Java
            refactorFromTo(
                    """
                import java.util.List;
                import org.gradle.api.Project;
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Project project, List<Provider<Integer>> providers) {
                        project.provider(() -> providers.get(0).get() + "hi");
                    }
                }
                """,
                    """
                import java.util.List;
                import org.gradle.api.Project;
                import org.gradle.api.provider.Provider;

                class Test {
                    void test(Project project, List<Provider<Integer>> providers) {
                        providers.get(0).map(providersGet0Value -> providersGet0Value + "hi");
                    }
                }
                """);
        }
    }

    private void refactorFromTo(String input, String output) {
        bestEffortRefactoringValidator()
                .addInputLines("Test.java", input)
                .addOutputLines("Test.java", output)
                .doTest();
    }

    private RefactoringValidator bestEffortRefactoringValidator() {
        return refactoringValidator("-XepOpt:GradleGuide:BestEffortMode");
    }

    private RefactoringValidator refactoringValidator(String... args) {
        return RefactoringValidator.of(ProviderGet.class, getClass(), args);
    }
}
