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

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.CompilationTestHelper;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.util.Name;
import java.util.StringJoiner;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CallGraphBugCheckerTest2 {
    private final CompilationTestHelper compilationTestHelper =
            CompilationTestHelper.newInstance(TestableCallGraphBugChecker.class, getClass());

    private TestableCallGraphBugChecker checker;

    @BeforeEach
    void beforeEach() {
        checker = new TestableCallGraphBugChecker();
    }

    @Test
    void testDirectCallsOnly_NoTransitiveCalls() {
        test(
                """
            class TestClass {
                // BUG: Diagnostic contains: CallGraph: TestClass.f1 --> [ TestClass.f2, ]
                public void f1() {
                    // BUG: Diagnostic contains: CallGraph Edge: TestClass.f2
                    f2();
                }

                // BUG: Diagnostic contains: CallGraph: TestClass.f2 --> [ TestClass.f3, ]
                public void f2() {
                    // BUG: Diagnostic contains: CallGraph Edge: TestClass.f3
                    f3();
                }

                // BUG: Diagnostic contains: CallGraph: TestClass.f3 --> [ , ]
                public void f3() {
                    // leaf method
                }
            }
            """);
    }

    @Test
    void testMultipleCallsToSameMethod() {
        test(
                """
            class TestClass {
                // BUG: Diagnostic contains: CallGraph: TestClass.f1 --> [ TestClass.f2, ]
                public void f1() {
                    // BUG: Diagnostic contains: CallGraph Edge: TestClass.f2
                    f2();
                    // BUG: Diagnostic contains: CallGraph Edge: TestClass.f2
                    f2();
                }

                // BUG: Diagnostic contains: CallGraph: TestClass.f2 --> [ TestClass.f3, ]
                public void f2() {
                    // BUG: Diagnostic contains: CallGraph Edge: TestClass.f3
                    f3();
                }

                // BUG: Diagnostic contains: CallGraph: TestClass.f3 --> [ , ]
                public void f3() {
                    // leaf method
                }
            }
            """);
    }

    @Test
    void testMultipleDirectCalls() {
        test(
                """
            class TestClass {
                // BUG: Diagnostic contains: CallGraph: TestClass.caller --> [ TestClass.method1, TestClass.method2, ]
                public void caller() {
                    // BUG: Diagnostic contains: CallGraph Edge: TestClass.method1
                    method1();
                    // BUG: Diagnostic contains: CallGraph Edge: TestClass.method2
                    method2();
                }

                // BUG: Diagnostic contains: CallGraph: TestClass.method1 --> [ TestClass.helper, ]
                public void method1() {
                    // BUG: Diagnostic contains: CallGraph Edge: TestClass.helper
                    helper();
                }

                // BUG: Diagnostic contains: CallGraph: TestClass.method2 --> [ TestClass.helper, ]
                public void method2() {
                    // BUG: Diagnostic contains: CallGraph Edge: TestClass.helper
                    helper();
                }

                // BUG: Diagnostic contains: CallGraph: TestClass.helper --> [ , ]
                public void helper() {
                    // leaf method
                }
            }
            """);
    }

    @Test
    void testDirectAndTransitiveCalls() {
        test(
                """
            class TestClass {
                // BUG: Diagnostic contains: CallGraph: TestClass.caller --> [ TestClass.helper, TestClass.method1, ]
                public void caller() {
                    // BUG: Diagnostic contains: CallGraph Edge: TestClass.helper
                    helper();
                    // BUG: Diagnostic contains: CallGraph Edge: TestClass.method1
                    method1();
                }

                // BUG: Diagnostic contains: CallGraph: TestClass.method1 --> [ TestClass.helper, ]
                public void method1() {
                    // BUG: Diagnostic contains: CallGraph Edge: TestClass.helper
                    helper();
                }

                // BUG: Diagnostic contains: CallGraph: TestClass.helper --> [ , ]
                public void helper() {
                    // leaf method
                }
            }
            """);
    }

    @Test
    void testNoMethodCalls() {
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
    void testRecursiveMethod() {
        test(
                """
            class TestClass {
                // BUG: Diagnostic contains: CallGraph: TestClass.recursive --> [ TestClass.recursive, ]
                public void recursive(int n) {
                    if (n > 0) {
                        // BUG: Diagnostic contains: CallGraph Edge: TestClass.recursive
                        recursive(n - 1);
                    }
                }
            }
            """);
    }

    @Test
    void testChainedMethodCalls() {
        test(
                """
            import java.util.ArrayList;
            import java.util.List;

            class TestClass {
                // BUG: Diagnostic contains: CallGraph: TestClass.chainCaller --> [ TestClass.Builder.build, TestClass.Builder.setName, TestClass.Builder.setValue, TestClass.getBuilder, ]
                public void chainCaller() {
                    // BUG: Diagnostic contains: CallGraph Edge: TestClass.getBuilder
                    // CallGraph Edge: TestClass.Builder.setName
                    // CallGraph Edge: TestClass.Builder.setValue
                    // CallGraph Edge: TestClass.Builder.build
                    getBuilder().setName("test").setValue(42).build();
                }

                // BUG: Diagnostic contains: CallGraph: TestClass.fluentCaller --> [ TestClass.Service.processData, TestClass.Service.save, TestClass.Service.transform, TestClass.getService, ]
                public void fluentCaller() {
                    // BUG: Diagnostic contains: CallGraph Edge: TestClass.getService
                    this.getService()
                    // BUG: Diagnostic contains: CallGraph Edge: TestClass.Service.processData
                        .processData()
                    // BUG: Diagnostic contains: CallGraph Edge: TestClass.Service.transform
                        .transform()
                    // BUG: Diagnostic contains: CallGraph Edge: TestClass.Service.save
                        .save();
                }

                // BUG: Diagnostic contains: CallGraph: TestClass.mixedChaining --> [ TestClass.getList, TestClass.getString, java.lang.String.toLowerCase, java.lang.String.trim, java.util.List.add, ]
                public void mixedChaining() {
                    // BUG: Diagnostic contains: CallGraph Edge: TestClass.getString
                    // CallGraph Edge: java.lang.String.trim
                    // CallGraph Edge: java.lang.String.toLowerCase
                    String result = getString().trim().toLowerCase();
                    // BUG: Diagnostic contains: CallGraph Edge: TestClass.getList
                    // CallGraph Edge: java.util.List.add
                    getList().add(result);
                }

                // BUG: Diagnostic contains: CallGraph: TestClass.getBuilder --> [ , ]
                public Builder getBuilder() { return new Builder(); }
                // BUG: Diagnostic contains: CallGraph: TestClass.getService --> [ , ]
                public Service getService() { return new Service(); }
                // BUG: Diagnostic contains: CallGraph: TestClass.getString --> [ , ]
                public String getString() { return "example"; }
                // BUG: Diagnostic contains: CallGraph: TestClass.getList --> [ , ]
                public List<String> getList() { return new ArrayList<>(); }

                public static class Builder {
                    // BUG: Diagnostic contains: CallGraph: TestClass.Builder.setName --> [ , ]
                    public Builder setName(String name) { return this; }
                    // BUG: Diagnostic contains: CallGraph: TestClass.Builder.setValue --> [ , ]
                    public Builder setValue(int value) { return this; }
                    // BUG: Diagnostic contains: CallGraph: TestClass.Builder.build --> [ , ]
                    public Object build() { return new Object(); }
                }

                public static class Service {
                    // BUG: Diagnostic contains: CallGraph: TestClass.Service.processData --> [ , ]
                    public Service processData() { return this; }
                    // BUG: Diagnostic contains: CallGraph: TestClass.Service.transform --> [ , ]
                    public Service transform() { return this; }
                    // BUG: Diagnostic contains: CallGraph: TestClass.Service.save --> [ , ]
                    public void save() { }
                }
            }
            """);
    }

    @Test
    void testComplexChaining() {
        test(
                """
            class TestClass {
                // BUG: Diagnostic contains: CallGraph: TestClass.complexChain --> [ TestClass.Builder.build, TestClass.Chain.fourth, TestClass.Chain.second, TestClass.Inner.getResult, TestClass.Inner.process, TestClass.Outer.getInner, TestClass.first, TestClass.getBuilder, TestClass.getOuter, TestClass.setData, TestClass.third, ]
                public void complexChain() {
                    // BUG: Diagnostic contains: CallGraph Edge: TestClass.getOuter
                    // CallGraph Edge: TestClass.Outer.getInner
                    // CallGraph Edge: TestClass.Inner.process
                    // CallGraph Edge: TestClass.Inner.getResult
                    getOuter().getInner().process().getResult();

                    // BUG: Diagnostic contains: CallGraph Edge: TestClass.getBuilder
                    // CallGraph Edge: TestClass.Builder.build
                    // CallGraph Edge: TestClass.setData
                    setData(getBuilder().build());

                    // BUG: Diagnostic contains: CallGraph Edge: TestClass.first
                    // CallGraph Edge: TestClass.Chain.second
                    first().second();

                    // BUG: Diagnostic contains: CallGraph Edge: TestClass.third
                    // CallGraph Edge: TestClass.Chain.fourth
                    third().fourth();
                }

                // BUG: Diagnostic contains: CallGraph: TestClass.getOuter --> [ , ]
                public Outer getOuter() { return new Outer(); }

                // BUG: Diagnostic contains: CallGraph: TestClass.getBuilder --> [ , ]
                public Builder getBuilder() { return new Builder(); }

                // BUG: Diagnostic contains: CallGraph: TestClass.setData --> [ , ]
                public void setData(Object data) { }

                // BUG: Diagnostic contains: CallGraph: TestClass.first --> [ , ]
                public Chain first() { return new Chain(); }

                // BUG: Diagnostic contains: CallGraph: TestClass.third --> [ , ]
                public Chain third() { return new Chain(); }

                public static class Outer {
                    // BUG: Diagnostic contains: CallGraph: TestClass.Outer.getInner --> [ , ]
                    public Inner getInner() { return new Inner(); }
                }

                public static class Inner {
                    // BUG: Diagnostic contains: CallGraph: TestClass.Inner.process --> [ , ]
                    public Inner process() { return this; }

                    // BUG: Diagnostic contains: CallGraph: TestClass.Inner.getResult --> [ , ]
                    public Object getResult() { return new Object(); }
                }

                public static class Builder {
                    // BUG: Diagnostic contains: CallGraph: TestClass.Builder.build --> [ , ]
                    public Object build() { return new Object(); }
                }

                public static class Chain {
                    // BUG: Diagnostic contains: CallGraph: TestClass.Chain.second --> [ , ]
                    public Chain second() { return this; }

                    // BUG: Diagnostic contains: CallGraph: TestClass.Chain.fourth --> [ , ]
                    public Chain fourth() { return this; }
                }
            }

            """);
    }

    @Test
    void testLongMethodChain() {
        test(
                """
            class TestClass {
                // BUG: Diagnostic contains: CallGraph: TestClass.longChain --> [ TestClass.getValue, java.lang.Object.toString, java.lang.String.concat, java.lang.String.replace, java.lang.String.substring, java.lang.String.toLowerCase, java.lang.String.trim, ]
                public void longChain() {
                    // BUG: Diagnostic contains: CallGraph Edge: TestClass.getValue
                    getValue()
                    // BUG: Diagnostic contains: CallGraph Edge: java.lang.Object.toString
                        .toString()
                    // BUG: Diagnostic contains: CallGraph Edge: java.lang.String.trim
                        .trim()
                    // BUG: Diagnostic contains: CallGraph Edge: java.lang.String.toLowerCase
                        .toLowerCase()
                    // BUG: Diagnostic contains: CallGraph Edge: java.lang.String.substring
                        .substring(0, 5)
                    // BUG: Diagnostic contains: CallGraph Edge: java.lang.String.replace
                        .replace("a", "b")
                    // BUG: Diagnostic contains: CallGraph Edge: java.lang.String.concat
                        .concat("suffix");
                }

                // BUG: Diagnostic contains: CallGraph: TestClass.getValue --> [ , ]
                public Object getValue() {
                    return "example";
                }
            }
            """);
    }

    @AutoService(BugChecker.class)
    @BugPattern(summary = "", severity = SeverityLevel.SUGGESTION)
    private static final class TestableCallGraphBugChecker extends CallGraphBugChecker
            implements BugChecker.MethodInvocationTreeMatcher, BugChecker.MethodTreeMatcher {
        @Override
        public MoreInfoLink moreInfoLink() {
            return null;
        }

        @Override
        public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
            MethodSymbol caller = ASTHelpers.getSymbol(
                    (MethodTree) state.findPathToEnclosing(MethodTree.class).getLeaf());
            MethodSymbol callee = (MethodSymbol) ASTHelpers.getSymbol(tree.getMethodSelect());
            callGraph.get(state).callGraph.edgeValue(caller, callee).ifPresent(invocTrees -> {
                if (invocTrees.contains(tree)) {
                    String outgoingNode = getFullyQualifiedName(callee).toString();
                    String diagnostic = String.format("CallGraph Edge: %s", outgoingNode);
                    state.reportMatch(
                            buildDescription(tree).setMessage(diagnostic).build());
                }
            });
            return Description.NO_MATCH;
        }

        public static Name getFullyQualifiedName(MethodSymbol sym) {
            return sym.owner.getQualifiedName().append('.', sym.getQualifiedName());
        }

        @Override
        public Description matchMethod(MethodTree tree, VisitorState state) {
            MethodSymbol curr = ASTHelpers.getSymbol(tree);
            String methodName = getFullyQualifiedName(curr).toString();
            StringJoiner listOfNeighbours = new StringJoiner(", ", "[ ", ", ]");
            callGraph.get(state).callGraph.successors(curr).stream()
                    .map(TestableCallGraphBugChecker::getFullyQualifiedName)
                    .map(Name::toString)
                    .sorted()
                    .forEach(listOfNeighbours::add);
            String outgoingNodesSorted = String.format("%s --> %s", methodName, listOfNeighbours);
            String diagnostic = String.format("CallGraph: %s", outgoingNodesSorted);
            state.reportMatch(buildDescription(tree).setMessage(diagnostic).build());
            return Description.NO_MATCH;
        }
    }

    private void test(@Language("Java") String source) {
        compilationTestHelper.addSourceLines("Test.java", source).doTest();
    }
}
