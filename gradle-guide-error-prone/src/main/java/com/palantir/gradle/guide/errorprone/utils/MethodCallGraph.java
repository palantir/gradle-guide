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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.Traverser;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePathScanner;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * A directed graph of "who is called by who" within this compilation unit (source file).
 */
public final class MethodCallGraph {
    private static final LoadingCache<CompilationUnitTree, MethodCallGraph> callGraphCache =
            Caffeine.newBuilder().maximumSize(1_000).weakKeys().softValues().build(MethodCallGraph::new);

    /**
     * Builds the `MethodCallGraph` of this Compilation Unit at the first call, and returns the cached graph in
     * subsequent calls.
     */
    public static MethodCallGraph getOrBuild(CompilationUnitTree compilationUnitTree) {
        return callGraphCache.get(compilationUnitTree);
    }

    /**
     * Nodes are {@code MethodSymbol}s of methods declared within this compilation unit.
     * There is an edge from {@code f} to {@code g} iff {@code f} is called by {@code g}.
     */
    private final ImmutableGraph<MethodSymbol> callGraph;

    private MethodCallGraph(CompilationUnitTree compilationUnitTree) {
        MutableGraph<MethodSymbol> mutableCallGraph =
                GraphBuilder.directed().allowsSelfLoops(true).build();

        new TreePathScanner<Void, Optional<MethodSymbol>>() {
            @Override
            public Void visitMethod(MethodTree node, Optional<MethodSymbol> caller) {
                MethodSymbol methodSymbol = ASTHelpers.getSymbol(node);
                mutableCallGraph.addNode(methodSymbol);
                return super.visitMethod(node, Optional.of(methodSymbol));
            }
        }.scan(compilationUnitTree, Optional.empty());

        new TreePathScanner<Void, Optional<MethodSymbol>>() {
            @Override
            public Void visitMethod(MethodTree node, Optional<MethodSymbol> caller) {
                MethodSymbol methodSymbol = ASTHelpers.getSymbol(node);
                return super.visitMethod(node, Optional.of(methodSymbol));
            }

            @Override
            public Void visitMethodInvocation(MethodInvocationTree node, Optional<MethodSymbol> caller) {
                MethodSymbol calleeSymbol = (MethodSymbol) ASTHelpers.getSymbol(node.getMethodSelect());
                if (mutableCallGraph.nodes().contains(calleeSymbol) && caller.isPresent()) {
                    MethodSymbol callerSymbol = caller.get();
                    mutableCallGraph.putEdge(calleeSymbol, callerSymbol);
                }
                return super.visitMethodInvocation(node, caller);
            }
        }.scan(compilationUnitTree, Optional.empty());

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
