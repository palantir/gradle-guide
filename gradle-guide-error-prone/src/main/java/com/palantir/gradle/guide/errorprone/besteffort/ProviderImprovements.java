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

package com.palantir.gradle.guide.errorprone.besteffort;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.palantir.gradle.guide.errorprone.GradleGuideBugChecker;
import com.palantir.gradle.guide.errorprone.utils.ReplacementTracker.SuggestedFixBuilder;
import com.palantir.gradle.guide.errorprone.utils.TreeUtils;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;

@AutoService(BugChecker.class)
@BugPattern(
        severity = SeverityLevel.SUGGESTION,
        summary = "Do not call `Provider.get`. Instead, pass providers directly to methods that accept them, "
                + "or transform providers using `Provider.map` or `Provider.flatMap`, or combine providers using "
                + "`Provider.zip`. Calling `Provider.get` causes Gradle to lose track of implicit dependencies and "
                + "can lead to timing issues by reading values too early.")
public final class ProviderImprovements extends GradleGuideBugChecker
        implements BugChecker.MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> MATCHER = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.provider.Provider")
            .namedAnyOf("get");

    private static final Matcher<ExpressionTree> NEW_PROVIDER_MATCHER = Matchers.instanceMethod()
            .onDescendantOfAny("org.gradle.api.Project", "org.gradle.api.provider.ProviderFactory")
            .named("provider");

    private static final Matcher<ExpressionTree> DEPENDS_ON_MATCHER =
            Matchers.instanceMethod().onDescendantOf("org.gradle.api.Task").named("dependsOn");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!MATCHER.matches(tree, state) || !bestEffortModeEnabled(state)) {
            return Description.NO_MATCH;
        }

        Description.Builder description = buildDescription(tree);
        SuggestedFixBuilder fix = new SuggestedFixBuilder();

        if (!(tree.getMethodSelect() instanceof MemberSelectTree memberSelectTree)) {
            return description.build();
        }

        // Check if this is a TaskProvider.get() being passed to dependsOn
        if (handleDependsOnTaskProviderGet(state, fix, tree, memberSelectTree)) {
            return buildDescription(tree).addFix(fix.build()).build();
        }

        // Handle the existing case
        replaceNewProviderWithGetCalledInside(state, fix, tree, memberSelectTree);

        return buildDescription(tree).addFix(fix.build()).build();
    }

    @SuppressWarnings("for-rollout:NullAway")
    private static boolean handleDependsOnTaskProviderGet(
            VisitorState state, SuggestedFixBuilder fix, MethodInvocationTree tree, MemberSelectTree memberSelectTree) {

        // Check if the parent is a method invocation
        if (!(state.getPath().getParentPath().getLeaf() instanceof MethodInvocationTree parentMethodInvocation)) {
            return false;
        }

        // Check if the parent method is dependsOn
        if (!DEPENDS_ON_MATCHER.matches(parentMethodInvocation, state)) {
            return false;
        }

        // Check if this get() call is an argument to dependsOn
        for (ExpressionTree arg : parentMethodInvocation.getArguments()) {
            if (arg.equals(tree)) {
                // Replace the provider.get() with just provider
                fix.replace(tree, state.getSourceForNode(memberSelectTree.getExpression()));
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("for-rollout:NullAway")
    private static void replaceNewProviderWithGetCalledInside(
            VisitorState state, SuggestedFixBuilder fix, MethodInvocationTree tree, MemberSelectTree memberSelectTree) {

        TreeUtils.pathToRoot(state.getPath())
                .filter(path -> path.getParentPath().getLeaf() instanceof LambdaExpressionTree
                        && path.getParentPath().getParentPath().getLeaf() instanceof MethodInvocationTree)
                .findFirst()
                .ifPresent(lambdaBodyPath -> {
                    MethodInvocationTree providerFactoryMethod = (MethodInvocationTree)
                            lambdaBodyPath.getParentPath().getParentPath().getLeaf();

                    if (!NEW_PROVIDER_MATCHER.matches(providerFactoryMethod, state)) {
                        return;
                    }

                    String originalMethodExpression = state.getSourceForNode(memberSelectTree.getExpression());

                    boolean lambdaBodyJustReturnsProviderValue = tree == lambdaBodyPath.getLeaf();
                    if (lambdaBodyJustReturnsProviderValue) {
                        fix.replace(providerFactoryMethod, originalMethodExpression);
                        return;
                    }

                    CharSequence sourceCode = state.getSourceCode();

                    String lambdaProviderArg =
                            TreeUtils.expressionToIdentifier(state, memberSelectTree.getExpression())
                                            .orElse("provider") + "Value";

                    String lambdaBodyChanged = sourceCode.subSequence(
                                    TreeUtils.startPosition(lambdaBodyPath.getLeaf()),
                                    TreeUtils.startPosition(memberSelectTree))
                            + lambdaProviderArg
                            + sourceCode.subSequence(
                                    state.getEndPosition(tree), state.getEndPosition(lambdaBodyPath.getLeaf()));

                    fix.replace(
                            providerFactoryMethod,
                            originalMethodExpression + ".map(" + lambdaProviderArg + " -> " + lambdaBodyChanged + ")");
                });
    }

    @Override
    public MoreInfoLink moreInfoLink() {
        return new MoreInfoHeadingLink("avoiding-unnecessary-configuration.md", "Using `TaskProvider`s");
    }
}
