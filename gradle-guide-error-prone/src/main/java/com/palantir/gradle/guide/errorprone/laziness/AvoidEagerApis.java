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
import com.palantir.gradle.guide.errorprone.utils.ChainedCallMatcher;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@AutoService(BugChecker.class)
@BugPattern(severity = SeverityLevel.ERROR, summary = "Avoid eager API methods, which force tasks to be realized. ")
public final class AvoidEagerApis extends GradleGuideBugChecker implements BugChecker.MethodInvocationTreeMatcher {
    private static final Matcher<MethodInvocationTree> FIRST_ARGUMENT_IS_MAP =
            Matchers.argument(0, Matchers.isSubtypeOf(Map.class));

    private static final Matcher<MethodInvocationTree> SECOND_ARGUMENT_IS_GROOVY_CLOSURE =
            Matchers.argument(1, Matchers.isSubtypeOf("groovy.lang.Closure"));

    private static final Matcher<MethodInvocationTree> NO_DIRECT_REGISTER_EQUIVALENT =
            Matchers.anyOf(FIRST_ARGUMENT_IS_MAP, SECOND_ARGUMENT_IS_GROOVY_CLOSURE);

    // Eager APIs that should be avoided for configuration avoidance
    private static final Matcher<ExpressionTree> CONTAINER_CREATE = Matchers.instanceMethod()
            .onDescendantOfAny("org.gradle.api.NamedDomainObjectContainer")
            .namedAnyOf("create");
    private static final Matcher<ExpressionTree> TASK_CONTAINER_GET_BY_PATH = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.tasks.TaskContainer")
            .namedAnyOf("getByPath");
    private static final Matcher<ExpressionTree> TASK_CONTAINER_FIND_BY_PATH = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.tasks.TaskContainer")
            .namedAnyOf("findByPath");
    private static final Matcher<ExpressionTree> TASK_CONTAINER_REPLACE = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.tasks.TaskContainer")
            .namedAnyOf("replace");
    private static final Matcher<ExpressionTree> TASK_CONTAINER_GET_BY_NAME = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.tasks.TaskContainer")
            .namedAnyOf("getByName");
    private static final Matcher<ExpressionTree> TASK_CONTAINER_FIND_BY_NAME = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.tasks.TaskContainer")
            .namedAnyOf("findByName");
    private static final Matcher<ExpressionTree> COLLECTION_ALL = Matchers.instanceMethod()
            .onDescendantOfAny("org.gradle.api.tasks.TaskCollection", "org.gradle.api.DomainObjectCollection")
            .namedAnyOf("all");
    private static final Matcher<ExpressionTree> TASK_COLLECTION_WHEN_TASK_ADDED = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.tasks.TaskContainer")
            .namedAnyOf("whenTaskAdded");
    private static final Matcher<ExpressionTree> COLLECTION_WHEN_OBJECT_ADDED = Matchers.instanceMethod()
            .onDescendantOfAny("org.gradle.api.DomainObjectCollection")
            .namedAnyOf("whenObjectAdded");
    private static final Matcher<ExpressionTree> COLLECTION_MATCHING = Matchers.instanceMethod()
            .onDescendantOfAny("org.gradle.api.tasks.TaskCollection", "org.gradle.api.DomainObjectCollection")
            .namedAnyOf("matching");
    private static final Matcher<ExpressionTree> TASK_COLLECTION_GET_AT = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.tasks.TaskCollection")
            .namedAnyOf("getAt");
    private static final Matcher<ExpressionTree> COLLECTION_WITH_TYPE = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.DomainObjectCollection")
            .namedAnyOf("withType");
    private static final Matcher<ExpressionTree> SET_FIND_ALL = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.NamedDomainObjectSet")
            .namedAnyOf("findAll");
    private static final Matcher<ExpressionTree> COLLECTION_FIND_BY_NAME = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.NamedDomainObjectSet")
            .namedAnyOf("findByName");

    // Define eager API usage rules
    private static final List<EagerApiUsage> EAGER_API_USAGES = List.of(
            EagerApiUsage.fix(
                    ChainedCallMatcher.of(CONTAINER_CREATE),
                    "Use `.register` instead",
                    AvoidEagerApis::fixCreateToRegister),
            EagerApiUsage.fix( // fix
                    ChainedCallMatcher.of(TASK_CONTAINER_GET_BY_NAME),
                    "Use `tasks.named(...)` instead of `tasks.getByName(...)`",
                    AvoidEagerApis::fixGetByNameToNamed),
            EagerApiUsage.report( // fix
                    ChainedCallMatcher.of(TASK_CONTAINER_FIND_BY_NAME),
                    "Use `tasks.named(...)` instead of `tasks.findByName(...)`"),
            EagerApiUsage.report(
                    ChainedCallMatcher.of(TASK_CONTAINER_GET_BY_PATH),
                    "Use `tasks.named(...)` instead of `tasks.getByPath(...)`"),
            EagerApiUsage.report(
                    ChainedCallMatcher.of(TASK_CONTAINER_FIND_BY_PATH),
                    "Use `tasks.named(...)` instead of `tasks.findByPath(...)`"),
            EagerApiUsage.report(
                    ChainedCallMatcher.of(TASK_CONTAINER_REPLACE),
                    "Avoid `replace()` - forces eager resolution and behavior may change"),
            EagerApiUsage.report(
                    ChainedCallMatcher.of(TASK_COLLECTION_WHEN_TASK_ADDED),
                    "Use `tasks.configureEach(...)` instead of `tasks.whenTaskAdded(...)`"),
            EagerApiUsage.report( // fix
                    ChainedCallMatcher.of(COLLECTION_MATCHING),
                    "Use `configureEach(...)` with conditional logic instead of `matching(...)`"),
            EagerApiUsage.report(
                    ChainedCallMatcher.of(TASK_COLLECTION_GET_AT), "Use `named(...)` instead of `getAt(...)`"),
            EagerApiUsage.report( // fix
                    ChainedCallMatcher.of(COLLECTION_WITH_TYPE),
                    "Use `named(...).configureEach(...)` instead of `withType(...)`"),
            EagerApiUsage.report(
                    ChainedCallMatcher.of(COLLECTION_ALL), "Use `configureEach(...)` instead of `all(...)`"),
            EagerApiUsage.report(
                    ChainedCallMatcher.of(COLLECTION_WHEN_OBJECT_ADDED),
                    "Use `configureEach(...)` instead of `whenObjectAdded(...)`"),
            EagerApiUsage.report(
                    ChainedCallMatcher.of(SET_FIND_ALL),
                    "Avoid `findAll()` - use `matching(...)` and `configureEach(...)` instead"),
            EagerApiUsage.report(
                    ChainedCallMatcher.of(COLLECTION_FIND_BY_NAME),
                    "Use `named()` to avoid eager configuration. Note that it fails if the task does not exist"));

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        EAGER_API_USAGES.stream()
                .filter(usage -> usage.matches(tree, state))
                .findFirst()
                .ifPresent(eagerApiUsage -> eagerApiUsage.fixOrReport(tree, state, this));

        return Description.NO_MATCH;
    }

    private static SuggestedFix fixGetByNameToNamed(MethodInvocationTree tree, VisitorState state) {
        if (!bestEffortModeEnabled(state)) {
            return SuggestedFix.emptyFix();
        }

        // If the second argument is a closure, there is no direct java equivalent
        if (SECOND_ARGUMENT_IS_GROOVY_CLOSURE.matches(tree, state)) {
            return SuggestedFix.emptyFix();
        }

        SuggestedFix.Builder fix = SuggestedFix.builder();

        // Handle both cases:
        // MyTask myTask = (myTask) project.getTasks().named(...).get();
        // Task myTask = project.getTasks().named(...).get();
        // TODO(okelvin): We could handle chained calls here but it's complicated
        Tree parentLeaf = state.getPath().getParentPath().getLeaf();
        Tree toReplace;
        Optional<String> taskClass;
        if (parentLeaf instanceof TypeCastTree castTree) {
            toReplace = parentLeaf;
            taskClass = Optional.of(state.getSourceForNode(castTree.getType()) + ".class");
        } else {
            toReplace = tree;
            taskClass = Optional.empty();
        }

        fix.postfixWith(toReplace, ".get()");

        String name = state.getSourceForNode(tree.getArguments().get(0));
        if (taskClass.isEmpty()) {
            fix.replace(
                    toReplace,
                    state.getSourceForNode(ASTHelpers.getReceiver(tree.getMethodSelect())) + ".named(" + name + ")");
        } else {
            fix.replace(
                    toReplace,
                    state.getSourceForNode(ASTHelpers.getReceiver(tree.getMethodSelect())) + ".named(" + name + ", "
                            + taskClass.get() + ")");
        }
        return fix.build();
    }

    private static SuggestedFix fixCreateToRegister(MethodInvocationTree tree, VisitorState state) {
        if (!bestEffortModeEnabled(state)) {
            return SuggestedFix.emptyFix();
        }

        // If the first argument is a map, or second is a Closure, there isn't an equivalent
        // `.register` method to move to (from Java code at least)
        if (NO_DIRECT_REGISTER_EQUIVALENT.matches(tree, state)) {
            return SuggestedFix.emptyFix();
        }

        SuggestedFix.Builder fix = SuggestedFix.builder();
        fix.postfixWith(tree, ".get()");
        fix.replace(
                tree.getMethodSelect(),
                state.getSourceForNode(ASTHelpers.getReceiver(tree.getMethodSelect())) + ".register");
        return fix.build();
    }

    @Override
    public MoreInfoLink moreInfoLink() {
        return new MoreInfoHeadingLink("avoiding-unnecessary-configuration.md", "Lazy task registration");
    }
}
