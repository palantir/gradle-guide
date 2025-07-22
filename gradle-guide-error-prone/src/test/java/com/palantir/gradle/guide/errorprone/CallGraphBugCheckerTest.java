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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.auto.service.AutoService;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraph;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.palantir.gradle.guide.errorprone.CallGraphBugChecker.MethodCallGraph;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.util.Context;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;
import java.util.Set;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CallGraphBugCheckerTest {

    private TestableCallGraphBugChecker checker;

    @BeforeEach
    void beforeEach() {
        checker = new TestableCallGraphBugChecker();
    }

    @Test
    void testDirectCallsOnly_NoTransitiveCalls() throws Exception {
        String javaCode =
                """
            class TestClass {
                public void f1() {
                    f2();
                }

                public void f2() {
                    f3();
                }

                public void f3() {
                    // leaf method
                }
            }
            """;

        VisitorState state = parseJavaCode(javaCode);

        MethodCallGraph callGraph = checker.new MethodCallGraph(state);
        ValueGraph<MethodSymbol, Set<MethodInvocationTree>> graph = peekInternalGraph(callGraph);

        // Find methods by name
        MethodSymbol f1 = findMethodByName(graph.nodes(), "f1");
        MethodSymbol f2 = findMethodByName(graph.nodes(), "f2");
        MethodSymbol f3 = findMethodByName(graph.nodes(), "f3");

        assertNotNull(f1, "f1 method should be in graph");
        assertNotNull(f2, "f2 method should be in graph");
        assertNotNull(f3, "f3 method should be in graph");

        // Verify f1 calls only f2 (not f3 transitively)
        Set<MethodSymbol> f1Callees = graph.successors(f1);
        assertTrue(containsMethodNamed(f1Callees, "f2"), "f1 should call f2");
        assertFalse(containsMethodNamed(f1Callees, "f3"), "f1 should NOT call f3 transitively");

        // Verify f2 calls only f3
        Set<MethodSymbol> f2Callees = graph.successors(f2);
        assertTrue(containsMethodNamed(f2Callees, "f3"), "f2 should call f3");

        // Verify f3 calls no other methods
        Set<MethodSymbol> f3Callees = graph.successors(f3);
        assertTrue(f3Callees.isEmpty(), "f3 should call no other methods");
    }
    
    @Test
    void testMultipleCallsToSameMethod() throws Exception {
        String javaCode =
                """
            class TestClass {
                public void f1() {
                    f2(); // First call to f2
                    f2(); // Second call to f2
                }

                public void f2() {
                    f3();
                }

                public void f3() {
                    // leaf method
                }
            }
            """;

        VisitorState state = parseJavaCode(javaCode);

        MethodCallGraph callGraph = checker.new MethodCallGraph(state);
        ValueGraph<MethodSymbol, Set<MethodInvocationTree>> graph = peekInternalGraph(callGraph);

        // Find methods by name
        MethodSymbol f1 = findMethodByName(graph.nodes(), "f1");
        MethodSymbol f2 = findMethodByName(graph.nodes(), "f2");
        MethodSymbol f3 = findMethodByName(graph.nodes(), "f3");

        assertNotNull(f1, "f1 method should be in graph");
        assertNotNull(f2, "f2 method should be in graph");
        assertNotNull(f3, "f3 method should be in graph");

        // Verify f1 calls f2 twice
        Set<MethodSymbol> f1Callees = graph.successors(f1);
        assertTrue(containsMethodNamed(f1Callees, "f2"), "f1 should call f2");
        
        // Get the edge value between f1 and f2, which should contain both invocations
        Set<MethodInvocationTree> f1ToF2Calls = graph.edgeValue(f1, f2).orElseThrow();
        assertEquals(2, f1ToF2Calls.size(), "f1 should call f2 exactly twice");
        
        // Verify f1 doesn't call f3 directly
        assertFalse(containsMethodNamed(f1Callees, "f3"), "f1 should NOT call f3 directly");
        
        // Verify f2 calls f3
        Set<MethodSymbol> f2Callees = graph.successors(f2);
        assertTrue(containsMethodNamed(f2Callees, "f3"), "f2 should call f3");
        
        // Verify f3 calls no other methods
        Set<MethodSymbol> f3Callees = graph.successors(f3);
        assertTrue(f3Callees.isEmpty(), "f3 should call no other methods");
    }

    @Test
    void testMultipleDirectCalls() throws Exception {
        String javaCode =
                """
            class TestClass {
                public void caller() {
                    method1();
                    method2();
                }

                public void method1() {
                    helper();
                }

                public void method2() {
                    helper();
                }

                public void helper() {
                    // leaf method
                }
            }
            """;

        VisitorState state = parseJavaCode(javaCode);

        MethodCallGraph callGraph = checker.new MethodCallGraph(state);
        ValueGraph<MethodSymbol, Set<MethodInvocationTree>> graph = peekInternalGraph(callGraph);

        MethodSymbol caller = findMethodByName(graph.nodes(), "caller");
        MethodSymbol method1 = findMethodByName(graph.nodes(), "method1");
        MethodSymbol method2 = findMethodByName(graph.nodes(), "method2");

        // Verify caller calls method1 and method2 directly
        Set<MethodSymbol> callerCallees = graph.successors(caller);
        assertTrue(containsMethodNamed(callerCallees, "method1"), "caller should call method1");
        assertTrue(containsMethodNamed(callerCallees, "method2"), "caller should call method2");
        assertFalse(containsMethodNamed(callerCallees, "helper"), "caller should NOT call helper transitively");

        // Verify method1 and method2 each call helper
        Set<MethodSymbol> method1Callees = graph.successors(method1);
        Set<MethodSymbol> method2Callees = graph.successors(method2);
        assertTrue(containsMethodNamed(method1Callees, "helper"), "method1 should call helper");
        assertTrue(containsMethodNamed(method2Callees, "helper"), "method2 should call helper");
    }

    @Test
    void testDirectAndTransitiveCalls() throws Exception {
        String javaCode =
                """
            class TestClass {
                public void caller() {
                    helper();
                    method1();
                }

                public void method1() {
                    helper();
                }

                public void helper() {
                    // leaf method
                }
            }
            """;

        VisitorState state = parseJavaCode(javaCode);

        MethodCallGraph callGraph = checker.new MethodCallGraph(state);
        ValueGraph<MethodSymbol, Set<MethodInvocationTree>> graph = peekInternalGraph(callGraph);

        MethodSymbol caller = findMethodByName(graph.nodes(), "caller");
        MethodSymbol method1 = findMethodByName(graph.nodes(), "method1");

        // Verify caller calls method1, helper directly
        Set<MethodSymbol> callerCallees = graph.successors(caller);
        assertTrue(containsMethodNamed(callerCallees, "method1"), "caller should call method1");
        assertTrue(containsMethodNamed(callerCallees, "helper"), "caller should call helper");

        // Verify method1 calls helper
        Set<MethodSymbol> method1Callees = graph.successors(method1);
        assertTrue(containsMethodNamed(method1Callees, "helper"), "method1 should call helper");
    }

    @Test
    void testNoMethodCalls() throws Exception {
        String javaCode =
                """
            class TestClass {
                public void standalone() {
                    int x = 42;
                    System.out.println(x);
                }
            }
            """;

        VisitorState state = parseJavaCode(javaCode);

        MethodCallGraph callGraph = checker.new MethodCallGraph(state);
        ValueGraph<MethodSymbol, Set<MethodInvocationTree>> graph = peekInternalGraph(callGraph);

        MethodSymbol standalone = findMethodByName(graph.nodes(), "standalone");
        assertNotNull(standalone, "standalone method should be in graph");

        Set<MethodSymbol> callees = graph.successors(standalone);
        assertNotNull(callees, "callees set should not be null");
    }

    @Test
    void testRecursiveMethod() throws Exception {
        String javaCode =
                """
            class TestClass {
                public void recursive(int n) {
                    if (n > 0) {
                        recursive(n - 1);
                    }
                }
            }
            """;
        VisitorState state = parseJavaCode(javaCode);

        MethodCallGraph callGraph = checker.new MethodCallGraph(state);
        ValueGraph<MethodSymbol, Set<MethodInvocationTree>> graph = peekInternalGraph(callGraph);

        MethodSymbol recursive = findMethodByName(graph.nodes(), "recursive");
        assertNotNull(recursive, "recursive method should be in graph");

        Set<MethodSymbol> callees = graph.successors(recursive);
        assertTrue(callees.contains(recursive), "recursive method should call itself");
    }

    @Test
    void testChainedMethodCalls() throws Exception {
        String javaCode =
                """
            import java.util.ArrayList;
            import java.util.List;

            class TestClass {
                public void chainCaller() {
                    getBuilder().setName("test").setValue(42).build();
                }

                public void fluentCaller() {
                    this.getService()
                        .processData()
                        .transform()
                        .save();
                }

                public void mixedChaining() {
                    String result = getString().trim().toLowerCase();
                    getList().add(result);
                }

                // Helper methods to make the example complete
                public Builder getBuilder() { return new Builder(); }
                public Service getService() { return new Service(); }
                public String getString() { return "example"; }
                public List<String> getList() { return new ArrayList<>(); }

                // Mock classes for the example
                public static class Builder {
                    public Builder setName(String name) { return this; }
                    public Builder setValue(int value) { return this; }
                    public Object build() { return new Object(); }
                }

                public static class Service {
                    public Service processData() { return this; }
                    public Service transform() { return this; }
                    public void save() { }
                }
            }
            """;
        VisitorState state = parseJavaCode(javaCode);
        MethodCallGraph callGraph = checker.new MethodCallGraph(state);
        ValueGraph<MethodSymbol, Set<MethodInvocationTree>> graph = peekInternalGraph(callGraph);

        // Test chainCaller method
        MethodSymbol chainCaller = findMethodByName(graph.nodes(), "chainCaller");
        assertNotNull(chainCaller, "chainCaller method should be in graph");

        Set<MethodSymbol> chainCallerCallees = graph.successors(chainCaller);
        assertTrue(containsMethodNamed(chainCallerCallees, "getBuilder"), "chainCaller should call getBuilder");
        assertTrue(containsMethodNamed(chainCallerCallees, "setName"), "chainCaller should call setName");
        assertTrue(containsMethodNamed(chainCallerCallees, "setValue"), "chainCaller should call setValue");
        assertTrue(containsMethodNamed(chainCallerCallees, "build"), "chainCaller should call build");

        // Test fluentCaller method
        MethodSymbol fluentCaller = findMethodByName(graph.nodes(), "fluentCaller");
        assertNotNull(fluentCaller, "fluentCaller method should be in graph");

        Set<MethodSymbol> fluentCallerCallees = graph.successors(fluentCaller);
        assertTrue(containsMethodNamed(fluentCallerCallees, "getService"), "fluentCaller should call getService");
        assertTrue(containsMethodNamed(fluentCallerCallees, "processData"), "fluentCaller should call processData");
        assertTrue(containsMethodNamed(fluentCallerCallees, "transform"), "fluentCaller should call transform");
        assertTrue(containsMethodNamed(fluentCallerCallees, "save"), "fluentCaller should call save");

        // Test mixedChaining method
        MethodSymbol mixedChaining = findMethodByName(graph.nodes(), "mixedChaining");
        assertNotNull(mixedChaining, "mixedChaining method should be in graph");

        Set<MethodSymbol> mixedChainingCallees = graph.successors(mixedChaining);
        assertTrue(containsMethodNamed(mixedChainingCallees, "getString"), "mixedChaining should call getString");
        assertTrue(containsMethodNamed(mixedChainingCallees, "trim"), "mixedChaining should call trim");
        assertTrue(containsMethodNamed(mixedChainingCallees, "toLowerCase"), "mixedChaining should call toLowerCase");
        assertTrue(containsMethodNamed(mixedChainingCallees, "getList"), "mixedChaining should call getList");
        assertTrue(containsMethodNamed(mixedChainingCallees, "add"), "mixedChaining should call add on the list");
    }

    @Test
    void testComplexChaining() throws Exception {
        String javaCode =
                """
            class TestClass {
                public void complexChain() {
                    // Nested chaining
                    getOuter().getInner().process().getResult();

                    // Chaining with method calls as parameters
                    setData(getBuilder().build());

                    // Multiple separate chains
                    first().second();
                    third().fourth();
                }

                // Helper methods
                public Outer getOuter() { return new Outer(); }
                public Builder getBuilder() { return new Builder(); }
                public void setData(Object data) { }
                public Chain first() { return new Chain(); }
                public Chain third() { return new Chain(); }

                public static class Outer {
                    public Inner getInner() { return new Inner(); }
                }

                public static class Inner {
                    public Inner process() { return this; }
                    public Object getResult() { return new Object(); }
                }

                public static class Builder {
                    public Object build() { return new Object(); }
                }

                public static class Chain {
                    public Chain second() { return this; }
                    public Chain fourth() { return this; }
                }
            }
            """;

        VisitorState state = parseJavaCode(javaCode);

        MethodCallGraph callGraph = checker.new MethodCallGraph(state);
        ValueGraph<MethodSymbol, Set<MethodInvocationTree>> graph = peekInternalGraph(callGraph);

        MethodSymbol complexChain = findMethodByName(graph.nodes(), "complexChain");
        assertNotNull(complexChain, "complexChain method should be in graph");

        Set<MethodSymbol> callees = graph.successors(complexChain);
        assertTrue(containsMethodNamed(callees, "getOuter"), "should call getOuter");
        assertTrue(containsMethodNamed(callees, "getInner"), "should call getInner (chained)");
        assertTrue(containsMethodNamed(callees, "process"), "should call process (chained)");
        assertTrue(containsMethodNamed(callees, "getResult"), "should call getResult (chained)");

        assertTrue(containsMethodNamed(callees, "getBuilder"), "should call getBuilder");
        assertTrue(containsMethodNamed(callees, "build"), "should call build (chained)");
        assertTrue(containsMethodNamed(callees, "setData"), "should call setData (direct call)");

        assertTrue(containsMethodNamed(callees, "first"), "should call first");
        assertTrue(containsMethodNamed(callees, "second"), "should call second (chained)");
        assertTrue(containsMethodNamed(callees, "third"), "should call third");
        assertTrue(containsMethodNamed(callees, "fourth"), "should call fourth (chained)");
    }

    @Test
    void testLongMethodChain() throws Exception {
        String javaCode =
                """
            class TestClass {
                public void longChain() {
                    getValue()
                        .toString()
                        .trim()
                        .toLowerCase()
                        .substring(0, 5)
                        .replace("a", "b")
                        .concat("suffix");
                }

                public Object getValue() { return "example"; }
            }
            """;

        VisitorState state = parseJavaCode(javaCode);
        MethodCallGraph callGraph = checker.new MethodCallGraph(state);
        ValueGraph<MethodSymbol, Set<MethodInvocationTree>> graph = peekInternalGraph(callGraph);

        MethodSymbol longChain = findMethodByName(graph.nodes(), "longChain");
        assertNotNull(longChain, "longChain method should be in graph");

        Set<MethodSymbol> callees = graph.successors(longChain);
        assertTrue(containsMethodNamed(callees, "getValue"), "should call getValue");
        assertTrue(containsMethodNamed(callees, "toString"), "should call toString (chained)");
        assertTrue(containsMethodNamed(callees, "trim"), "should call trim (chained)");
        assertTrue(containsMethodNamed(callees, "toLowerCase"), "should call toLowerCase (chained)");
        assertTrue(containsMethodNamed(callees, "substring"), "should call substring (chained)");
        assertTrue(containsMethodNamed(callees, "replace"), "should call replace (chained)");
        assertTrue(containsMethodNamed(callees, "concat"), "should call concat (chained)");

        // Verify we have the expected number of direct calls
        long methodCallCount = callees.stream()
                .filter(method -> !method.getSimpleName().toString().equals("longChain"))
                .count();
        assertEquals(7, methodCallCount, "should have exactly 7 direct calls");
    }

    @AutoService(BugChecker.class)
    @BugPattern(summary = "", severity = SeverityLevel.SUGGESTION)
    private static final class TestableCallGraphBugChecker extends CallGraphBugChecker {
        @Override
        public MoreInfoLink moreInfoLink() {
            return null;
        }
    }

    public static MutableValueGraph<MethodSymbol, Set<MethodInvocationTree>> peekInternalGraph(MethodCallGraph graph)
            throws NoSuchFieldException, IllegalAccessException {
        Class<?> methodCallGraphClass = graph.getClass();
        Field callGraphField = methodCallGraphClass.getDeclaredField("callGraph");
        callGraphField.setAccessible(true);
        return (MutableValueGraph<MethodSymbol, Set<MethodInvocationTree>>) callGraphField.get(graph);
    }

    private VisitorState parseJavaCode(String javaCode) throws IOException {
        JavacTool tool = JavacTool.create();
        JavaFileObject sourceFile = new StringJavaFileObject("TestClass.java", javaCode);
        Context context = new Context();
        JavaFileManager fileManager = new JavacFileManager(context, true, UTF_8);

        JavacTask task = tool.getTask(null, fileManager, null, null, null, List.of(sourceFile));
        Iterable<? extends CompilationUnitTree> compilationUnits = task.parse();
        task.analyze(); // This is important for symbol resolution

        CompilationUnitTree compilationUnit = compilationUnits.iterator().next();
        TreePath path = new TreePath(compilationUnit);
        return VisitorState.createForUtilityPurposes(context).withPath(path);
    }

    private MethodSymbol findMethodByName(Set<MethodSymbol> methods, String name) {
        return methods.stream()
                .filter(method -> method.getSimpleName().toString().equals(name))
                .findFirst()
                .orElse(null);
    }

    private boolean containsMethodNamed(Set<MethodSymbol> methods, String name) {
        return methods.stream()
                .anyMatch(method -> method.getSimpleName().toString().equals(name));
    }

    // Helper class for creating in-memory Java source files
    private static class StringJavaFileObject extends SimpleJavaFileObject {
        private final String code;

        StringJavaFileObject(String name, String code) {
            super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
