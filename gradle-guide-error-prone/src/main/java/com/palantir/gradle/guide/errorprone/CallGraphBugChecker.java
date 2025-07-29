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
         * Represents a method call with optional chained call information.
         * For example, in {@code getProject().getLogger()}, {@code rootCall = getProject()},
         * {@code chainedCall = getProject().getLogger()}
         *
         */
        public record MethodCall(MethodInvocationTree rootCall, Optional<MethodInvocationTree> chainedCall) {}

        /**
         * Nodes are method declarations. The edge between two methods {@code f} and {@code g} are all the instances
         * where {@code f} calls {@code g} directly. The edge does not contain any transitive calls from {@code f} to
         * {@code g} (e.g. {@code f} calls {@code h} calls {@code g}). For more details, see
         * {@code CallGraphBugCheckerTest} for the graph's specification
         */
        @VisibleForTesting
        protected final MutableValueGraph<MethodSymbol, Set<MethodCall>> callGraph =
                ValueGraphBuilder.directed().allowsSelfLoops(true).build();

        // Separate graph construction into two passes for clarity. We can combine them later if perf becomes an issue.
        public MethodCallGraph(VisitorState state) {
            // First pass: build a map from each method call to the call chained to it (if any)
            Map<MethodInvocationTree, MethodInvocationTree> callToChainedCall = new HashMap<>();
            new SuppressibleTreePathScanner<Void, Optional<MethodInvocationTree>>(state) {
                @Override
                public Void visitMethodInvocation(
                        MethodInvocationTree call, Optional<MethodInvocationTree> chainedCall) {
                    chainedCall.ifPresent(chained -> callToChainedCall.put(call, chained));
                    return super.visitMethodInvocation(call, Optional.of(call));
                }
            }.scan(state.getPath().getCompilationUnit(), Optional.empty());

            // Second pass: build the call graph
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
                        Set<MethodCall> existingEdges =
                                callGraph.edgeValue(callerSymbol, calleeSymbol).orElseGet(HashSet::new);

                        Optional<MethodInvocationTree> chainedCall = Optional.ofNullable(callToChainedCall.get(node));
                        existingEdges.add(new MethodCall(node, chainedCall));
                        callGraph.putEdgeValue(callerSymbol, calleeSymbol, existingEdges);
                    }

                    return super.visitMethodInvocation(node, caller);
                }
            }.scan(state.getPath().getCompilationUnit(), Optional.empty());
        }

        /**
         * Returns a set of method calls that are reachable from {@code start} within this compilation unit (source file)
         */
        public Set<MethodCall> callsUnderTransitiveClosureOf(MethodSymbol start) {
            Set<MethodCall> result = new HashSet<>();

            Traverser<MethodSymbol> traverser = Traverser.forGraph(callGraph);
            for (MethodSymbol current : traverser.depthFirstPreOrder(start)) {
                for (MethodSymbol neighbor : callGraph.successors(current)) {
                    Set<MethodCall> callsToNeighbour =
                            callGraph.edgeValue(current, neighbor).orElseGet(Set::of);
                    result.addAll(callsToNeighbour);
                }
            }

            return result;
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
