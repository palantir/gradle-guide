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
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
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

    private static final Matcher<Tree> UNUSED_RETURN_VALUE =
            Matchers.parentNode(Matchers.isInstance(ExpressionStatementTree.class));

    private static final Matcher<MethodInvocationTree> FIRST_ARGUMENT_IS_MAP =
            Matchers.argument(0, Matchers.isSubtypeOf(Map.class));

    private static final Matcher<MethodInvocationTree> SECOND_ARGUMENT_IS_GROOVY_CLOSURE =
            Matchers.argument(1, Matchers.isSubtypeOf("groovy.lang.Closure"));

    private static final Matcher<MethodInvocationTree> NO_DIRECT_REGISTER_EQUIVALENT =
            Matchers.anyOf(FIRST_ARGUMENT_IS_MAP, SECOND_ARGUMENT_IS_GROOVY_CLOSURE);

    private static final Matcher<MethodInvocationTree> THIRD_ARGUMENT_IS_ACTION =
            Matchers.argument(2, Matchers.isSubtypeOf("org.gradle.api.Action"));

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!MATCHER.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        Description.Builder descriptionBuilder = buildDescription(tree);

        // If the return value is not used, we can replace `.create` with `.register` without worrying about
        // breaking usages of the return value as it's changed from a `Task` to a `TaskProvider`,
        // `Configuration` to `NamedDomainObjectProvider<Configuration>`, etc.
        if (UNUSED_RETURN_VALUE.matches(tree, state)) {
            // If the first argument is a map, or second is a Closure, there isn't an equivalent
            // `.register` method to move to (from Java code at least)
            if (NO_DIRECT_REGISTER_EQUIVALENT.matches(tree, state)) {
                return descriptionBuilder.build();
            }

            // Even if the return value is not used, we should not change the method call if the configure action
            // calls afterEvaluate, as we may now cause the object to be realised after the configuration phase,
            // which will cause afterEvaluate to throw.
            // I'm mainly worried about a couple of cases to do with `publications.create` here, as since it's to
            // do with publishing the error may not be discovered until publish time.
            if (THIRD_ARGUMENT_IS_ACTION.matches(tree, state)) {
                boolean actionCallsAfterEvaluate =
                        state.getSourceForNode(tree.getArguments().get(2)).contains("afterEvaluate");

                if (actionCallsAfterEvaluate) {
                    return descriptionBuilder.build();
                }
            }

            return descriptionBuilder
                    .addFix(SuggestedFix.replace(
                            tree.getMethodSelect(),
                            state.getSourceForNode(ASTHelpers.getReceiver(tree.getMethodSelect())) + ".register"))
                    .build();
        }

        return descriptionBuilder.build();
    }

    @Override
    public MoreInfoLink moreInfoLink() {
        return new MoreInfoHeadingLink("avoiding-unnecessary-configuration.md", "Lazy task registration");
    }
}
