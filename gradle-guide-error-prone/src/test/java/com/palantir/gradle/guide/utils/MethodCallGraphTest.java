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

package com.palantir.gradle.guide.utils;

import com.google.common.graph.ImmutableGraph;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.CompilationTestHelper;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.palantir.gradle.guide.errorprone.GradleGuideBugChecker;
import com.palantir.gradle.guide.errorprone.utils.MethodCallGraph;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.util.Name;
import java.lang.reflect.Field;
import java.util.StringJoiner;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

@SuppressWarnings("LineLength")
class MethodCallGraphTest {
    private final CompilationTestHelper compilationTestHelper =
            CompilationTestHelper.newInstance(TestableCallGraphBugChecker.class, getClass());

    @Test
    void captures_direct_callers_but_not_transitive_callers() {
        test(
                """
            class TestClass {
                // BUG: Diagnostic contains: CallGraph: TestClass.f1 --> [ , ]
                public void f1() {
                    f2();
                }

                // BUG: Diagnostic contains: CallGraph: TestClass.f2 --> [ TestClass.f1, ]
                public void f2() {
                    f3();
                }

                // BUG: Diagnostic contains: CallGraph: TestClass.f3 --> [ TestClass.f2, ]
                public void f3() {
                    // leaf method
                }
            }
            """);
    }

    @Test
    void captures_multiple_callers() {
        test(
                """
            class TestClass {
                // BUG: Diagnostic contains: CallGraph: TestClass.caller --> [ , ]
                public void caller() {
                    method1();
                    method2();
                }

                // BUG: Diagnostic contains: CallGraph: TestClass.method1 --> [ TestClass.caller, ]
                public void method1() {
                    helper();
                }

                // BUG: Diagnostic contains: CallGraph: TestClass.method2 --> [ TestClass.caller, ]
                public void method2() {
                    helper();
                }

                // BUG: Diagnostic contains: CallGraph: TestClass.helper --> [ TestClass.method1, TestClass.method2, ]
                public void helper() {
                    // leaf method
                }
            }
            """);
    }

    @Test
    void captures_first_and_second_level_callers() {
        test(
                """
            class TestClass {
                // BUG: Diagnostic contains: CallGraph: TestClass.caller --> [ , ]
                public void caller() {
                    helper();
                    method1();
                }

                // BUG: Diagnostic contains: CallGraph: TestClass.method1 --> [ TestClass.caller, ]
                public void method1() {
                    helper();
                }

                // BUG: Diagnostic contains: CallGraph: TestClass.helper --> [ TestClass.caller, TestClass.method1, ]
                public void helper() {
                    // leaf method
                }
            }
            """);
    }

    @Test
    void correctly_represents_no_method_calls() {
        test(
                """
            class TestClass {
                // BUG: Diagnostic contains: CallGraph: TestClass.standalone --> [ , ]
                public void standalone() {
                    int x = 42;
                }
            }
            """);
    }

    @Test
    void captures_recursive_calls() {
        test(
                """
            class TestClass {
                // BUG: Diagnostic contains: CallGraph: TestClass.recursive --> [ TestClass.recursive, ]
                public void recursive(int n) {
                    if (n > 0) {
                        recursive(n - 1);
                    }
                }
            }
            """);
    }

    @Test
    void captures_chained_calls() {
        test(
                """
            import java.util.ArrayList;
            import java.util.List;

            class TestClass {
                // BUG: Diagnostic contains: CallGraph: TestClass.chainCaller --> [ , ]
                public void chainCaller() {
                    getBuilder().setName("test").setValue(42).build();
                }

                // BUG: Diagnostic contains: CallGraph: TestClass.getBuilder --> [ TestClass.chainCaller, ]
                public Builder getBuilder() { return new Builder(); }

                public static class Builder {
                    // BUG: Diagnostic contains: CallGraph: TestClass.Builder.setName --> [ TestClass.chainCaller, ]
                    public Builder setName(String name) { return this; }
                    // BUG: Diagnostic contains: CallGraph: TestClass.Builder.setValue --> [ TestClass.chainCaller, ]
                    public Builder setValue(int value) { return this; }
                    // BUG: Diagnostic contains: CallGraph: TestClass.Builder.build --> [ TestClass.chainCaller, ]
                    public Object build() { return new Object(); }
                }
            }
            """);
    }

    @Test
    void works_with_abstract_methods() {
        test(
                """
            abstract class TestClass {
                // BUG: Diagnostic contains: CallGraph: TestClass.action --> [ , ]
                public abstract void action();
                }
        """);
    }

    @SuppressWarnings("BugCheckerAutoService") // We load the BugChecker directly here, rather than via service loading
    @BugPattern(summary = "", severity = SeverityLevel.SUGGESTION)
    private static final class TestableCallGraphBugChecker extends GradleGuideBugChecker
            implements BugChecker.MethodTreeMatcher {

        private ImmutableGraph<MethodSymbol> peekInternalCallGraph(CompilationUnitTree compilationUnitTree) {
            MethodCallGraph callGraph = MethodCallGraph.getOrBuild(compilationUnitTree);

            try {
                Class<?> clazz = callGraph.getClass();
                Field field = clazz.getDeclaredField("callGraph");
                field.setAccessible(true);
                return (ImmutableGraph<MethodSymbol>) field.get(callGraph);
            } catch (IllegalAccessException | NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }

        private static Name getFullyQualifiedName(MethodSymbol sym) {
            return sym.owner.getQualifiedName().append('.', sym.getQualifiedName());
        }

        @Override
        public Description matchMethod(MethodTree tree, VisitorState state) {
            MethodSymbol curr = ASTHelpers.getSymbol(tree);
            String methodName = getFullyQualifiedName(curr).toString();
            StringJoiner listOfNeighbours = new StringJoiner(", ", "[ ", ", ]");

            CompilationUnitTree compilationUnitTree = state.getPath().getCompilationUnit();
            peekInternalCallGraph(compilationUnitTree).successors(curr).stream()
                    .map(TestableCallGraphBugChecker::getFullyQualifiedName)
                    .map(Name::toString)
                    .sorted()
                    .forEach(listOfNeighbours::add);
            String outgoingNodesSorted = String.format("%s --> %s", methodName, listOfNeighbours);
            String diagnostic = String.format("CallGraph: %s", outgoingNodesSorted);
            state.reportMatch(buildDescription(tree).setMessage(diagnostic).build());
            return Description.NO_MATCH;
        }

        @Override
        public MoreInfoLink moreInfoLink() {
            return null;
        }
    }

    private void test(@Language("Java") String source) {
        compilationTestHelper.addSourceLines("Test.java", source).doTest();
    }
}
