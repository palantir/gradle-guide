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

package com.palantir.gradle.guide.errorprone.laziness;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.palantir.gradle.guide.errorprone.GradleGuideBugChecker;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Modifier;

@AutoService(BugChecker.class)
@BugPattern(
        severity = SeverityLevel.ERROR,
        summary = "Private methods should return Provider types instead of unwrapped types when all "
                + "return statements call Provider.get()")
public final class PrivateMethodsShouldReturnProviders extends GradleGuideBugChecker
        implements BugChecker.CompilationUnitTreeMatcher {

    record MethodUsage(MethodInvocationTree callSite, VisitorState state, boolean isDirectCall) {}

    record MethodInfo(MethodTree declaration, List<ReturnTree> returnStatements, List<MethodUsage> usages) {}

    private static final Matcher<ExpressionTree> PROVIDER_GET = Matchers.instanceMethod()
            .onDescendantOfAny("org.gradle.api.provider.Provider", "org.gradle.api.tasks.TaskProvider")
            .named("get");

    @Override
    public Description matchCompilationUnit(CompilationUnitTree tree, VisitorState state) {
        if (!bestEffortModeEnabled(state)) {
            return Description.NO_MATCH;
        }

        // Find all private methods and collect their return statements
        Map<Symbol.MethodSymbol, MethodInfo> candidates = new HashMap<>();

        new TreeScanner<Void, Void>() {
            @Override
            public Void visitMethod(MethodTree node, Void unused) {
                Symbol.MethodSymbol methodSymbol = ASTHelpers.getSymbol(node);
                if (methodSymbol == null) {
                    return super.visitMethod(node, unused);
                }

                MethodInfo methodInfo = new MethodInfo(node, new ArrayList<>(), new ArrayList<>());

                // Find all return statements in this method
                new TreeScanner<Void, Void>() {
                    @Override
                    public Void visitReturn(ReturnTree returnTree, Void unused) {
                        methodInfo.returnStatements().add(returnTree);
                        return super.visitReturn(returnTree, unused);
                    }
                }.scan(node.getBody(), null);

                candidates.put(methodSymbol, methodInfo);
                return super.visitMethod(node, unused);
            }
        }.scan(tree, null);

        // Find all usages of those methods
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                Symbol.MethodSymbol methodSymbol = ASTHelpers.getSymbol(node);
                if (methodSymbol != null && candidates.containsKey(methodSymbol)) {
                    MethodInfo methodInfo = candidates.get(methodSymbol);
                    boolean isDirectCall = true; // Method references would be handled differently
                    methodInfo.usages().add(new MethodUsage(node, state, isDirectCall));
                }
                return super.visitMethodInvocation(node, unused);
            }
        }.scan(tree, null);

        // Find methods that should be refactored
        List<MethodInfo> refactorableMethods = new ArrayList<>();
        for (Map.Entry<Symbol.MethodSymbol, MethodInfo> entry : candidates.entrySet()) {
            MethodInfo methodInfo = entry.getValue();
            if (shouldRefactorMethod(methodInfo, state)) {
                refactorableMethods.add(methodInfo);
            }
        }

        if (!refactorableMethods.isEmpty()) {
            return buildRefactorDescription(refactorableMethods, state);
        }

        return Description.NO_MATCH;
    }

    private boolean shouldRefactorMethod(MethodInfo methodInfo, VisitorState state) {
        if (!methodInfo.declaration().getModifiers().getFlags().contains(Modifier.PRIVATE)) {
            return false;
        }

        if (methodInfo.returnStatements().isEmpty()) {
            return false;
        }

        if (methodInfo.usages().isEmpty()) {
            return false;
        }

        for (MethodUsage usage : methodInfo.usages()) {
            if (!usage.isDirectCall()) {
                return false;
            }
        }

        for (ReturnTree returnTree : methodInfo.returnStatements()) {
            if (returnTree.getExpression() == null) {
                return false;
            }
            if (!PROVIDER_GET.matches(returnTree.getExpression(), state)) {
                return false;
            }
        }

        return true;
    }

    private Description buildRefactorDescription(List<MethodInfo> refactorableMethods, VisitorState state) {
        SuggestedFix.Builder fix = SuggestedFix.builder();

        for (MethodInfo methodInfo : refactorableMethods) {
            String currentReturnTypeString = state.getSourceForNode(methodInfo.declaration().getReturnType());

            fix.addImport("org.gradle.api.provider.Provider");
            fix.replace(methodInfo.declaration().getReturnType(), "Provider<" + currentReturnTypeString + ">");

            for (ReturnTree returnTree : methodInfo.returnStatements()) {
                ExpressionTree returnExpr = returnTree.getExpression();
                if (returnExpr instanceof MethodInvocationTree getCall
                        && getCall.getMethodSelect() instanceof MemberSelectTree memberSelect) {
                    fix.replace(returnExpr, state.getSourceForNode(memberSelect.getExpression()));
                }
            }

            for (MethodUsage usage : methodInfo.usages()) {
                fix.postfixWith(usage.callSite(), ".get()");
            }
        }

        return buildDescription(refactorableMethods.get(0).declaration())
                .addFix(fix.build())
                .build();
    }


    @Override
    public MoreInfoLink moreInfoLink() {
        return new MoreInfoHeadingLink("avoiding-unnecessary-configuration.md", "Using `Provider`s");
    }
}
