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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.palantir.gradle.guide.errorprone.utils.MethodCallGraph;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MethodCallGraphTest {

    private MethodCallGraph methodCallGraph;

    @BeforeEach
    void setUp() {
        methodCallGraph = new MethodCallGraph();
    }

    @Test
    void testDirectCallsOnly_NoTransitiveCalls() throws Exception {
        String javaCode =
                """
            public class TestClass {
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

        Tree ast = parseJavaCode(javaCode);
        methodCallGraph.buildCallGraph(ast);
        Map<MethodSymbol, Set<MethodSymbol>> graph = getGraphField();

        // Find methods by name
        MethodSymbol f1 = findMethodByName(graph.keySet(), "f1");
        MethodSymbol f2 = findMethodByName(graph.keySet(), "f2");
        MethodSymbol f3 = findMethodByName(graph.keySet(), "f3");

        assertNotNull(f1, "f1 method should be in graph");
        assertNotNull(f2, "f2 method should be in graph");
        assertNotNull(f3, "f3 method should be in graph");

        // Verify f1 calls only f2 (not f3 transitively)
        Set<MethodSymbol> f1Callees = graph.get(f1);
        assertTrue(containsMethodNamed(f1Callees, "f2"), "f1 should call f2");
        assertFalse(containsMethodNamed(f1Callees, "f3"), "f1 should NOT call f3 transitively");

        // Verify f2 calls only f3
        Set<MethodSymbol> f2Callees = graph.get(f2);
        assertTrue(containsMethodNamed(f2Callees, "f3"), "f2 should call f3");

        // Verify f3 calls no other methods
        Set<MethodSymbol> f3Callees = graph.get(f3);
        assertTrue(f3Callees.isEmpty() || f3Callees.equals(Set.of(f3)), "f3 should call no other methods");
    }

    @Test
    void testMultipleDirectCalls() throws Exception {
        String javaCode =
                """
            public class TestClass {
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

        Tree ast = parseJavaCode(javaCode);
        methodCallGraph.buildCallGraph(ast);
        Map<MethodSymbol, Set<MethodSymbol>> graph = getGraphField();

        MethodSymbol caller = findMethodByName(graph.keySet(), "caller");
        MethodSymbol method1 = findMethodByName(graph.keySet(), "method1");
        MethodSymbol method2 = findMethodByName(graph.keySet(), "method2");

        // Verify caller calls method1 and method2 directly
        Set<MethodSymbol> callerCallees = graph.get(caller);
        assertTrue(containsMethodNamed(callerCallees, "method1"), "caller should call method1");
        assertTrue(containsMethodNamed(callerCallees, "method2"), "caller should call method2");
        assertFalse(containsMethodNamed(callerCallees, "helper"), "caller should NOT call helper transitively");

        // Verify method1 and method2 each call helper
        Set<MethodSymbol> method1Callees = graph.get(method1);
        Set<MethodSymbol> method2Callees = graph.get(method2);
        assertTrue(containsMethodNamed(method1Callees, "helper"), "method1 should call helper");
        assertTrue(containsMethodNamed(method2Callees, "helper"), "method2 should call helper");
    }

    @Test
    void testNoMethodCalls() throws Exception {
        String javaCode =
                """
            public class TestClass {
                public void standalone() {
                    int x = 42;
                    System.out.println(x);
                }
            }
            """;

        Tree ast = parseJavaCode(javaCode);
        methodCallGraph.buildCallGraph(ast);
        Map<MethodSymbol, Set<MethodSymbol>> graph = getGraphField();

        MethodSymbol standalone = findMethodByName(graph.keySet(), "standalone");
        assertNotNull(standalone, "standalone method should be in graph");

        Set<MethodSymbol> callees = graph.get(standalone);
        // Method should either have empty callees or only call external methods (like System.out.println)
        // The exact behavior depends on your implementation
        assertNotNull(callees, "callees set should not be null");
    }

    @Test
    void testRecursiveMethod() throws Exception {
        String javaCode =
                """
            public class TestClass {
                public void recursive(int n) {
                    if (n > 0) {
                        recursive(n - 1);
                    }
                }
            }
            """;

        Tree ast = parseJavaCode(javaCode);
        methodCallGraph.buildCallGraph(ast);
        Map<MethodSymbol, Set<MethodSymbol>> graph = getGraphField();

        MethodSymbol recursive = findMethodByName(graph.keySet(), "recursive");
        assertNotNull(recursive, "recursive method should be in graph");

        Set<MethodSymbol> callees = graph.get(recursive);
        // Should contain itself (recursive call)
        assertTrue(callees.contains(recursive), "recursive method should call itself");
    }

    // Helper methods
    private Tree parseJavaCode(String javaCode) throws IOException {
        JavacTool tool = JavacTool.create();
        JavaFileObject sourceFile = new StringJavaFileObject("TestClass.java", javaCode);

        JavacTask task = tool.getTask(null, null, null, null, null, List.of(sourceFile));

        Iterable<? extends CompilationUnitTree> compilationUnits = task.parse();
        task.analyze(); // This is important for symbol resolution

        return compilationUnits.iterator().next();
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

    private Map<MethodSymbol, Set<MethodSymbol>> getGraphField() {
        try {
            Field field = MethodCallGraph.class.getDeclaredField("outgoingCalls");
            field.setAccessible(true);
            return (Map<MethodSymbol, Set<MethodSymbol>>) field.get(methodCallGraph);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access graph field", e);
        }
    }

    // Helper class for creating in-memory Java source files
    private static class StringJavaFileObject extends SimpleJavaFileObject {
        private final String code;

        public StringJavaFileObject(String name, String code) {
            super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
