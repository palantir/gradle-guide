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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.Traverser;
import com.google.common.graph.ValueGraphBuilder;
import com.google.errorprone.VisitorState;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public abstract class CallGraphBugChecker extends GradleGuideBugChecker {
    /**
     * A directed graph of "who calls who, and where" within this compilation unit (source file).
     */
    protected class MethodCallGraph {
        /**
         * Nodes are method declarations. The edge between two methods {@code f} and {@code g} are all the instances
         * where {@code f} calls {@code g} directly. The edge does not contain any transitive calls from {@code f} to
         * {@code g} (e.g. {@code f} calls {@code h} calls {@code g}). For more details, see
         * {@code CallGraphBugCheckerTest} for the graph's specification
         */
        @VisibleForTesting
        protected final MutableValueGraph<MethodSymbol, Set<MethodInvocationTree>> callGraph =
                ValueGraphBuilder.directed().allowsSelfLoops(true).build();

        /**
         * This exists to be passed to {@code EdgeConsumer}s.
         *  e.g. if the {@code CompilationUnit} contains the chained call {@code getProject().getLogger()}, this will
         *      map {@code getProject() to getProject().getLogger()}
         *  In other words, it maps child calls to parent calls in the AST of the Compilation Unit.
         */
        private final Map<MethodInvocationTree, MethodInvocationTree> callToChainedCall = new HashMap<>();

        public MethodCallGraph(VisitorState state) {
            // Do two separate walks for clarity. Can merge them if perf becomes an issue.
            new SuppressibleTreePathScanner<Void, Optional<MethodSymbol>>(state) {
                @Override
                public Void visitMethod(MethodTree node, Optional<MethodSymbol> caller) {
                    MethodSymbol methodSymbol = ASTHelpers.getSymbol(node);
                    callGraph.addNode(methodSymbol);
                    return super.visitMethod(node, Optional.of(methodSymbol));
                }

                @Override
                public Void visitMethodInvocation(MethodInvocationTree node, Optional<MethodSymbol> caller) {
                    MethodSymbol calleeSymbol = (MethodSymbol) ASTHelpers.getSymbol(node.getMethodSelect());
                    callGraph.addNode(calleeSymbol);

                    if (caller.isPresent()) {
                        MethodSymbol callerSymbol = caller.get();
                        Set<MethodInvocationTree> existingInvocations =
                                callGraph.edgeValue(callerSymbol, calleeSymbol).orElseGet(HashSet::new);
                        existingInvocations.add(node);
                        callGraph.putEdgeValue(callerSymbol, calleeSymbol, existingInvocations);
                    }

                    return super.visitMethodInvocation(node, caller);
                }
            }.scan(state.getPath().getCompilationUnit(), Optional.empty());

            new SuppressibleTreePathScanner<Void, Optional<MethodInvocationTree>>(state) {
                @Override
                public Void visitMethodInvocation(
                        MethodInvocationTree call, Optional<MethodInvocationTree> chainedCall) {
                    chainedCall.ifPresent(chained -> callToChainedCall.put(call, chained));
                    return super.visitMethodInvocation(call, Optional.of(call));
                }
            }.scan(state.getPath().getCompilationUnit(), Optional.empty());
        }

        /**
         * Routine which processes an edge in the call graph.
         * {@code callToChainedCall} maps a method call to the call immediately chained to it.
         * We need the chained call to provide auto-fixes, e.g. getProject().getLogger() ==> getLogger()
         */
        public interface EdgeConsumer {
            void accept(
                    MethodSymbol from,
                    MethodSymbol to,
                    Set<MethodInvocationTree> edge,
                    Map<MethodInvocationTree, MethodInvocationTree> callToChainedCall);
        }

        /**
         * From {@code start}, visit all the methods called in {@code start} recursively.
         * Uses depth-first traversal and automatically handles cycle detection.
         *
         * @param consumer consumes all invocations to this method
         */
        public void dfs(MethodSymbol start, EdgeConsumer consumer) {
            Traverser<MethodSymbol> traverser = Traverser.forGraph(callGraph);

            for (MethodSymbol current : traverser.depthFirstPreOrder(start)) {
                for (MethodSymbol neighbor : callGraph.successors(current)) {
                    Set<MethodInvocationTree> edgeInvocations =
                            callGraph.edgeValue(current, neighbor).orElseGet(Set::of);
                    consumer.accept(current, neighbor, edgeInvocations, callToChainedCall);
                }
            }
        }
    }

    private MethodCallGraph callGraph;

    /**
     * The first call to this method builds the call graph on the state's compilation unit.
     * Subsequent calls will return the cached call graph.
     */
    protected MethodCallGraph getCallGraph(VisitorState state) {
        if (callGraph == null) {
            callGraph = new MethodCallGraph(state);
        }

        return callGraph;
    }
}
