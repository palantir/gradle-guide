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
import com.google.errorprone.util.ASTHelpers;
import com.palantir.logsafe.Preconditions;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * An incrementally built directed graph of "who calls who"
 */
public class MethodCallGraph {
    private static final Map<MethodSymbol, ImmutableSet<MethodSymbol>> outgoingCalls = new HashMap<>();

    public void buildCallGraph(Tree tree) {
        new TreeScanner<ImmutableSet<MethodSymbol>, Void>() {
            @Override
            public ImmutableSet<MethodSymbol> visitMethod(MethodTree node, Void unused) {
                MethodSymbol methodSymbol = ASTHelpers.getSymbol(node);
                return outgoingCalls.computeIfAbsent(methodSymbol, sym -> super.visitMethod(node, null));
            }

            @Override
            public ImmutableSet<MethodSymbol> visitMethodInvocation(MethodInvocationTree node, Void unused) {
                super.visitMethodInvocation(node, null);
                MethodSymbol methodSymbol = (MethodSymbol) ASTHelpers.getSymbol(node.getMethodSelect());
                return ImmutableSet.of(methodSymbol);
            }

            @Override
            public ImmutableSet<MethodSymbol> reduce(ImmutableSet<MethodSymbol> r1, ImmutableSet<MethodSymbol> r2) {
                return Stream.of(r1, r2)
                        .filter(Objects::nonNull)
                        .flatMap(Set::stream)
                        .collect(ImmutableSet.toImmutableSet());
            }
        }.scan(tree, null);
    }

    public ImmutableSet<MethodSymbol> getOutgoing(MethodTree tree) {
        MethodSymbol sym = ASTHelpers.getSymbol(tree);
        if (!outgoingCalls.containsKey(sym)) {
            buildCallGraph(tree);
        }

        return Preconditions.checkNotNull(outgoingCalls.get(sym), "Guaranteed by buildCallGraph");
    }
}
