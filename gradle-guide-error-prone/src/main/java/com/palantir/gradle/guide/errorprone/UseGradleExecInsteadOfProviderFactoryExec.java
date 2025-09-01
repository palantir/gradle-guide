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
@BugPattern(
        severity = SeverityLevel.ERROR,
        summary = "Use GradleExec.exec() instead of ProviderFactory.exec(). "
                + "GradleExec provides configuration cache compatibility, eliminates manual provider zipping, "
                + "and captures execution context for clear error messages instead of Gradle internal stack traces. "
                + "It returns a single FallibleProvider combining stdout, stderr, and exit code.")
public final class UseGradleExecInsteadOfProviderFactoryExec extends GradleGuideBugChecker
        implements BugChecker.MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> PROVIDER_FACTORY_EXEC = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.provider.ProviderFactory")
            .named("exec");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (PROVIDER_FACTORY_EXEC.matches(tree, state)) {
            return buildDescription(tree).build();
        }

        return Description.NO_MATCH;
    }

    @Override
    public MoreInfoLink moreInfoLink() {
        return new MoreInfoPageLink("adopting-the-configuration-cache.md");
    }
}
