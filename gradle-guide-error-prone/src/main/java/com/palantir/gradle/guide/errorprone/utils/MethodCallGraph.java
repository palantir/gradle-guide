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

package com.palantir.gradle.guide.errorprone.utils;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.Traverser;
import com.google.common.graph.ValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * An incrementally built directed graph of "who calls who".
 * Assume one single compilation unit.
 */
public class MethodCallGraph {

    private static final MutableValueGraph<MethodSymbol, Set<MethodInvocationTree>> callGraph =
            ValueGraphBuilder.directed().allowsSelfLoops(true).build();

    /**
     * Builds the call graph incrementally by scanning the provided tree.
     * @param tree The tree to scan for method calls.
     */
    public static void buildCallGraph(Tree tree) {
        new TreeScanner<Void, Optional<MethodSymbol>>() {
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
        }.scan(tree, Optional.empty());
    }

    /**
     * Retrieves the outgoing edges (callees) for a given method.
     * @param methodTree The method tree to query.
     * @return A set of MethodSymbol representing methods called by the input method.
     */
    public static ImmutableSet<MethodSymbol> getCallees(MethodTree methodTree) {
        MethodSymbol sym = ASTHelpers.getSymbol(methodTree);
        if (!callGraph.nodes().contains(sym)) {
            buildCallGraph(methodTree);
        }
        return ImmutableSet.copyOf(callGraph.successors(sym));
    }

    /**
     * Retrieves the set of MethodInvocationTree for a specific caller-callee edge.
     * @param caller The calling method symbol.
     * @param callee The called method symbol.
     * @return A set of MethodInvocationTree representing the locations of the calls.
     */
    public static ImmutableSet<MethodInvocationTree> getInvocationTrees(MethodSymbol caller, MethodSymbol callee) {
        return callGraph.edgeValue(caller, callee).map(ImmutableSet::copyOf).orElseGet(ImmutableSet::of);
    }

    /**
     * Provides access to the full graph for advanced querying or traversal.
     * @return An immutable view of the current call graph.
     */
    public static ValueGraph<MethodSymbol, Set<MethodInvocationTree>> getGraph() {
        return callGraph;
    }

    /**
     * Traverses the call graph starting from the given method, visiting all reachable nodes.
     * For each neighbor of a node, if the neighbor matches the provided criterion,
     * processes the edge (Set<MethodInvocationTree>) between the node and the neighbor using the provided action.
     * Uses depth-first traversal and automatically handles cycle detection.
     * @param start The starting method tree for traversal.
     * @param criterion A predicate to filter neighbors (e.g., check if neighbor is Task::getProject).
     * @param edgeAction A BiConsumer to process the edge data (Set<MethodInvocationTree>) for matching neighbors.
     */
    public static void scan(
            MethodTree start,
            Predicate<MethodSymbol> criterion,
            BiConsumer<MethodSymbol, Set<MethodInvocationTree>> edgeAction) {
        MethodSymbol startSymbol = ASTHelpers.getSymbol(start);

        if (!callGraph.nodes().contains(startSymbol)) {
            buildCallGraph(start);
        }

        Traverser<MethodSymbol> traverser = Traverser.forGraph(callGraph::successors);

        for (MethodSymbol current : traverser.depthFirstPreOrder(startSymbol)) {
            for (MethodSymbol neighbor : callGraph.successors(current)) {
                if (criterion.test(neighbor)) {
                    Set<MethodInvocationTree> edgeInvocations =
                            callGraph.edgeValue(current, neighbor).orElseGet(Set::of);
                    edgeAction.accept(neighbor, edgeInvocations);
                }
            }
        }
    }
}
