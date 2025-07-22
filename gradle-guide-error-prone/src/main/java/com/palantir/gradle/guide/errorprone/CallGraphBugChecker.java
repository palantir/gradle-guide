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

import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.Traverser;
import com.google.common.graph.ValueGraphBuilder;
import com.google.errorprone.VisitorState;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.util.ASTHelpers;
import com.palantir.gradle.guide.errorprone.utils.Cache;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

public abstract class CallGraphBugChecker extends GradleGuideBugChecker {
    /**
     * A directed graph of "who calls who, and where" within this compilation unit.
     */
    public class MethodCallGraph {
        protected final MutableValueGraph<MethodSymbol, Set<MethodInvocationTree>> callGraph =
                ValueGraphBuilder.directed().allowsSelfLoops(true).build();

        public MethodCallGraph(VisitorState state) {
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
        }

        /**
         * Starting from the given method, visit all the other methods called in this compilation unit, including
         * those transitively called (e.g. `start` calls `foo` calls `bar`).
         * Uses depth-first traversal and automatically handles cycle detection.
         *
         * @param edgeAction consumes all invocations to this method
         */
        public void scan(MethodTree start, BiConsumer<MethodSymbol, Set<MethodInvocationTree>> edgeAction) {
            MethodSymbol startSymbol = ASTHelpers.getSymbol(start);
            Traverser<MethodSymbol> traverser = Traverser.forGraph(callGraph);

            for (MethodSymbol current : traverser.depthFirstPreOrder(startSymbol)) {
                for (MethodSymbol neighbor : callGraph.successors(current)) {
                    Set<MethodInvocationTree> edgeInvocations =
                            callGraph.edgeValue(current, neighbor).orElseGet(Set::of);
                    edgeAction.accept(neighbor, edgeInvocations);
                }
            }
        }
    }

    @SuppressWarnings("VisibilityModifier")
    protected final Supplier<MethodCallGraph> callGraph = Cache.memoize(state -> new MethodCallGraph(state));
}
