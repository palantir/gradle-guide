/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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
import com.palantir.gradle.guide.errorprone.utils.ReplacementTracker.SuggestedFixBuilder;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.util.Map;

@AutoService(BugChecker.class)
@BugPattern(
        severity = SeverityLevel.ERROR,
        summary = "When registering a new `Task`, `Configuration` or other Gradle domain type, "
                + "use `.register` instead of `.create` to avoid realising the object eagerly "
                + "and performing unnecessary work which will slow down the build.")
public final class ConfigurationAvoidanceRegistration extends GradleGuideBugChecker
        implements BugChecker.MethodInvocationTreeMatcher {
    private static final Matcher<ExpressionTree> MATCHER = Matchers.instanceMethod()
            .onDescendantOfAny("org.gradle.api.NamedDomainObjectContainer")
            .namedAnyOf("create");

    private static final Matcher<MethodInvocationTree> FIRST_ARGUMENT_IS_MAP =
            Matchers.argument(0, Matchers.isSubtypeOf(Map.class));

    private static final Matcher<MethodInvocationTree> SECOND_ARGUMENT_IS_GROOVY_CLOSURE =
            Matchers.argument(1, Matchers.isSubtypeOf("groovy.lang.Closure"));

    private static final Matcher<MethodInvocationTree> NO_DIRECT_REGISTER_EQUIVALENT =
            Matchers.anyOf(FIRST_ARGUMENT_IS_MAP, SECOND_ARGUMENT_IS_GROOVY_CLOSURE);

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!MATCHER.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        Description.Builder description = buildDescription(tree);
        SuggestedFixBuilder fix = new SuggestedFixBuilder();

        if (!bestEffortModeEnabled(state)) {
            return description.build();
        }

        // If the first argument is a map, or second is a Closure, there isn't an equivalent
        // `.register` method to move to (from Java code at least)
        if (NO_DIRECT_REGISTER_EQUIVALENT.matches(tree, state)) {
            return description.build();
        }

        TreePath parentPath = state.getPath().getParentPath();
        Tree leaf = parentPath.getLeaf();

        if (leaf instanceof VariableTree variableTree) {
            replaceVariableDeclarationTypeWithProvider(state, fix, variableTree);
            replaceVariableUsagesWithTaskProviderGet(fix, parentPath);
        } else if (leaf instanceof ExpressionTree || leaf instanceof ReturnTree) {
            fix.postfixWith(tree, ".get()");
        }

        fix.replace(
                tree.getMethodSelect(),
                state.getSourceForNode(ASTHelpers.getReceiver(tree.getMethodSelect())) + ".register");

        return description.addFix(fix.build()).build();
    }

    private static void replaceVariableDeclarationTypeWithProvider(
            VisitorState state, SuggestedFix.Builder fixBuilder, VariableTree variableTree) {

        boolean isTask = state.getTypes()
                .isSubtype(ASTHelpers.getType(variableTree.getType()), state.getTypeFromString("org.gradle.api.Task"));

        if (isTask) {
            fixBuilder.addImport("org.gradle.api.tasks.TaskProvider");
            fixBuilder.prefixWith(variableTree.getType(), "TaskProvider<");
        } else {
            fixBuilder.addImport("org.gradle.api.NamedDomainObjectProvider");
            fixBuilder.prefixWith(variableTree.getType(), "NamedDomainObjectProvider<");
        }

        fixBuilder.postfixWith(variableTree.getType(), ">");
    }

    private static void replaceVariableUsagesWithTaskProviderGet(
            SuggestedFix.Builder fixBuilder, TreePath variableTreePath) {

        TreePath variableParent = variableTreePath.getParentPath();
        Object variableSymbol = ASTHelpers.getSymbol(variableTreePath.getLeaf());
        if (variableParent.getLeaf() instanceof BlockTree) {
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitIdentifier(IdentifierTree identifierTree, Void unused) {
                    if (variableSymbol.equals(ASTHelpers.getSymbol(identifierTree))) {
                        fixBuilder.postfixWith(identifierTree, ".get()");
                    }
                    return null;
                }
            }.scan(variableParent, null);
        }
    }

    @Override
    public MoreInfoLink moreInfoLink() {
        return new MoreInfoHeadingLink("avoiding-unnecessary-configuration.md", "Lazy task registration");
    }
}
