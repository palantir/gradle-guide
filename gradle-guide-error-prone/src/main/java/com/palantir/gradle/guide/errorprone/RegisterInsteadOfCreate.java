/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.guide.errorprone;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;

@AutoService(BugChecker.class)
@BugPattern(severity = SeverityLevel.ERROR, summary = "Don't do this yo")
public final class RegisterInsteadOfCreate extends GradleGuideBugChecker
        implements BugChecker.MethodInvocationTreeMatcher {
    private static final Matcher<ExpressionTree> MATCHER = Matchers.instanceMethod()
            .onDescendantOfAny("org.gradle.api.NamedDomainObjectContainer")
            .namedAnyOf("create");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!MATCHER.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        return buildDescription(tree).build();
    }

    @Override
    public MoreInfoLink moreInfoLink() {
        return new MoreInfoHeadingLink("diagnosing-build-performance.md", "Configuration subsection");
    }
}
