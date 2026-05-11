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
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.predicates.TypePredicates;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.VariableTree;
import java.util.Optional;
import javax.lang.model.element.Modifier;

@AutoService(BugChecker.class)
@BugPattern(
        severity = SeverityLevel.ERROR,
        summary = "Use Gradle's `Logger` instead of `SafeLogger` within Gradle plugins. "
                + "Safe logging isn't necessary for Gradle plugins which don't process sensitive data.")
public final class UseGradleLoggerInGradlePlugins extends GradleGuideBugChecker
        implements BugChecker.VariableTreeMatcher {
    private static final Matcher<ClassTree> GRADLE_PLUGIN = Matchers.isSubtypeOf("org.gradle.api.Plugin");
    private static final Matcher<VariableTree> SLF4J_LOGGER_IN_GRADLE_PLUGIN = Matchers.allOf(
            Matchers.isField(),
            Matchers.variableType(TypePredicates.allOf(
                    TypePredicates.isDescendantOf("org.slf4j.Logger"),
                    TypePredicates.not(TypePredicates.isDescendantOf("org.gradle.api.logging.Logger")))),
            Matchers.enclosingClass(GRADLE_PLUGIN));
    private static final Matcher<VariableTree> PRIVATE_STATIC_FINAL_SLF4J_LOGGER_IN_GRADLE_PLUGIN =
            Matchers.allOf(SLF4J_LOGGER_IN_GRADLE_PLUGIN, Matchers.hasModifier(Modifier.PRIVATE));
    private static final Matcher<VariableTree> SAFE_LOGGER_IN_GRADLE_PLUGIN = Matchers.allOf(
            Matchers.isField(),
            Matchers.variableType(TypePredicates.isDescendantOf("com.palantir.logsafe.logger.SafeLogger")),
            Matchers.enclosingClass(GRADLE_PLUGIN));

    @Override
    public Description matchVariable(VariableTree tree, VisitorState state) {
        if (SAFE_LOGGER_IN_GRADLE_PLUGIN.matches(tree, state)) {
            // Autofixing safe logging to Gradle logging is dangerous! Let the humans do it.
            return buildDescription(tree).build();
        } else if (PRIVATE_STATIC_FINAL_SLF4J_LOGGER_IN_GRADLE_PLUGIN.matches(tree, state)) {
            SuggestedFix fix = SuggestedFix.builder()
                    .merge(replaceWithGradleLogerDeclaration(tree, state))
                    .build();
            return buildDescription(tree).addFix(fix).build();
        } else if (SLF4J_LOGGER_IN_GRADLE_PLUGIN.matches(tree, state)) {

            return buildDescription(tree).build();
        } else {
            return Description.NO_MATCH;
        }
    }

    private static SuggestedFix replaceWithGradleLogerDeclaration(VariableTree loggerDeclaration, VisitorState state) {
        String loggerVarName = loggerDeclaration.getName().toString();
        Optional<ClassTree> enclosingClass =
                Optional.ofNullable(ASTHelpers.findEnclosingNode(state.getPath(), ClassTree.class));
        if (enclosingClass.isEmpty()) {
            throw new IllegalStateException("loggerDeclaration should always have an enclosing class");
        }

        String enclosingClassName = enclosingClass.get().getSimpleName().toString();
        String gradleLoggerDeclaration = String.format(
                "private static final Logger %s =" + " Logging.getLogger(%s.class);",
                loggerVarName, enclosingClassName);

        return SuggestedFix.builder()
                .removeImport("org.slf4j.Logger")
                .removeImport("org.slf4j.LoggerFactory")
                .addImport("org.gradle.api.logging.Logger")
                .addImport("org.gradle.api.logging.Logging")
                .replace(loggerDeclaration, gradleLoggerDeclaration)
                .build();
    }

    @Override
    public MoreInfoLink moreInfoLink() {
        return new MoreInfoHeadingLink("anatomy-of-a-gradle-plugin.md", "Plugins");
    }
}
