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
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.Traverser;
import com.google.errorprone.VisitorState;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public abstract class CallGraphBugChecker extends GradleGuideBugChecker {
    /**
     * A directed graph of "who is called by who" within this compilation unit (source file).
     */
    protected class MethodCallGraph {

        /**
         * Nodes are {@code MethodSymbol}s. There is an edge from {@code f} to {@code g} iff {@code f} is called by
         * {@code g}.
         */
        @VisibleForTesting
        protected final ImmutableGraph<MethodSymbol> callGraph;

        public MethodCallGraph(VisitorState state) {
            MutableGraph<MethodSymbol> mutableCallGraph =
                    GraphBuilder.directed().allowsSelfLoops(true).build();

            new SuppressibleTreePathScanner<Void, Optional<MethodSymbol>>(state) {
                @Override
                public Void visitMethod(MethodTree node, Optional<MethodSymbol> caller) {
                    MethodSymbol methodSymbol = ASTHelpers.getSymbol(node);
                    mutableCallGraph.addNode(methodSymbol);
                    return super.visitMethod(node, Optional.of(methodSymbol));
                }

                @Override
                public Void visitMethodInvocation(MethodInvocationTree node, Optional<MethodSymbol> caller) {
                    MethodSymbol calleeSymbol = (MethodSymbol) ASTHelpers.getSymbol(node.getMethodSelect());
                    mutableCallGraph.addNode(calleeSymbol);

                    if (caller.isPresent()) {
                        MethodSymbol callerSymbol = caller.get();
                        mutableCallGraph.putEdge(calleeSymbol, callerSymbol);
                    }

                    return super.visitMethodInvocation(node, caller);
                }
            }.scan(state.getPath().getCompilationUnit(), Optional.empty());

            callGraph = ImmutableGraph.copyOf(mutableCallGraph);
        }

        /**
         * Returns the set of methods that call {@code target} transitively within this compilation unit (source file).
         */
        public Set<MethodSymbol> transitiveCallers(MethodSymbol target) {
            Set<MethodSymbol> result = new HashSet<>();
            for (MethodSymbol node : Traverser.forGraph(callGraph).depthFirstPreOrder(target)) {
                result.addAll(callGraph.successors(node));
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
