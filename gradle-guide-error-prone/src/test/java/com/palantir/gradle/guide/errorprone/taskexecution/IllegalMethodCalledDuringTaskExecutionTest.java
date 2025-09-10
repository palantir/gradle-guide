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

import com.google.errorprone.CompilationTestHelper;
import com.palantir.gradle.guide.helpers.RefactoringValidator;
import org.intellij.lang.annotations.Language;

public abstract class IllegalMethodCalledDuringTaskExecutionTest {
    private final CompilationTestHelper compilationTestHelper =
            CompilationTestHelper.newInstance(IllegalMethodCalledDuringTaskExecution.class, getClass());

    protected final void test(@Language("Java") String source) {
        compilationTestHelper.addSourceLines("Test.java", source).doTest();
    }

    protected final void testFix(String filename, @Language("Java") String before, @Language("Java") String after) {
        RefactoringValidator.of(IllegalMethodCalledDuringTaskExecution.class, getClass())
                .addInputLines(filename, before)
                .addOutputLines(filename, after)
                .doTest();
    }
}
