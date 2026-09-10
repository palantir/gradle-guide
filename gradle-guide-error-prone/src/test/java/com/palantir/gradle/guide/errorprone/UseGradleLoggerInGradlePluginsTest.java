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
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

class UseGradleLoggerInGradlePluginsTest {
    private final CompilationTestHelper compilationTestHelper =
            CompilationTestHelper.newInstance(UseGradleLoggerInGradlePlugins.class, getClass());

    @Test
    void flag_safe_logger_instances() {
        test("""
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import com.palantir.logsafe.logger.SafeLogger;
            import com.palantir.logsafe.logger.SafeLoggerFactory;

            abstract class MyProject implements Plugin<Project> {
                // BUG: Diagnostic contains: Use Gradle's `Logger` instead of `SafeLogger` within Gradle plugins
                private static final SafeLogger log = SafeLoggerFactory.get(MyProject.class);

                @Override
                public void apply(Project project) {}
            }
            """);
    }

    @Test
    void autofix_slf4j_logger_instances() {
        testFix("MyProject.java", """
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;

            abstract class MyProject implements Plugin<Project> {
                private static final Logger log = LoggerFactory.getLogger(MyProject.class);

                @Override
                public void apply(Project project) {}
            }
            """, """
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.logging.Logger;
            import org.gradle.api.logging.Logging;

            abstract class MyProject implements Plugin<Project> {
                private static final Logger log = Logging.getLogger(MyProject.class);

                @Override
                public void apply(Project project) {}
            }
            """);
    }

    @Test
    void gradle_logger_instances_ok() {
        test("""
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.logging.Logger;
            import org.gradle.api.logging.Logging;

            abstract class MyProject implements Plugin<Project> {
                private static final Logger log = Logging.getLogger(MyProject.class);

                @Override
                public void apply(Project project) {}
            }
            """);
    }

    private void testFix(String filename, @Language("Java") String before, @Language("Java") String after) {
        RefactoringValidator.of(UseGradleLoggerInGradlePlugins.class, getClass())
                .addInputLines(filename, before)
                .addOutputLines(filename, after)
                .doTest();
    }

    private void test(@Language("Java") String source) {
        compilationTestHelper.addSourceLines("Test.java", source).doTest();
    }
}
