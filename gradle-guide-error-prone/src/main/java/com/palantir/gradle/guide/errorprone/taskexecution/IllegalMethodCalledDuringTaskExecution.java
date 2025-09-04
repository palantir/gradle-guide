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

package com.palantir.gradle.guide.errorprone.taskexecution;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.palantir.gradle.guide.errorprone.GradleGuideBugChecker;
import com.palantir.gradle.guide.errorprone.taskexecution.violations.GetProject;
import com.palantir.gradle.guide.errorprone.taskexecution.violations.GetProjectGetLogger;
import com.palantir.gradle.guide.errorprone.taskexecution.violations.Violation;
import com.palantir.gradle.guide.errorprone.utils.MethodCallGraph;
import com.palantir.gradle.guide.errorprone.utils.Tasks;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import one.util.streamex.StreamEx;

@AutoService(BugChecker.class)
@BugPattern(
        severity = SeverityLevel.ERROR,
        summary =
                """
        Don't call `getProject()` in task actions. Instead, your tasks should take in the "smallest" type
        required for the task's functionality. For example, instead getProject().version(), you should declare the
        project version as an `@Input public abstract Property<String>`.

        Doing so improves performance in two ways:
        1. It makes your tasks compatible with the configuration cache
        2. It increases task parallelism. When two tasks, such as printProjectName and printProjectVersion, both
        require the same Project object as input, they cannot run in parallel to prevent concurrent access.
        However, if their inputs are changed to Provider<String> name and Provider<String> version respectively,
        the tasks become independent and can execute in parallel.
        """)
public final class IllegalMethodCalledDuringTaskExecution extends GradleGuideBugChecker
        implements BugChecker.MethodInvocationTreeMatcher {

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        Optional<Violation> firstMatchingViolation = violations.stream()
                .filter(violation -> violation.matches(tree, state))
                .findFirst();
        if (firstMatchingViolation.isEmpty()) {
            return Description.NO_MATCH;
        }

        CompilationUnitTree compilationUnit = state.getPath().getCompilationUnit();
        MethodCallGraph callGraph = MethodCallGraph.getOrBuild(compilationUnit);

        Optional<MethodTree> enclosingMethodMaybe = Optional.ofNullable(ASTHelpers.findEnclosingMethod(state));
        if (enclosingMethodMaybe.isEmpty()) {
            return Description.NO_MATCH;
        }

        MethodSymbol enclosingMethod = ASTHelpers.getSymbol(enclosingMethodMaybe.get());
        // Find transitive callers of enclosing method first, as the illegal method e.g. getProject() is defined in
        // Gradle source, not the source file this BugChecker is running on.
        // If we included methods defined externally into the call graph,
        // IllegalMethodCalledDuringTaskExecutionTest#getProject_in_constructor_ok will fail.
        Set<MethodSymbol> transitiveCallersOfIllegalMethod = callGraph.transitiveCallers(enclosingMethod);

        boolean isInvokedAtTaskExecution = StreamEx.of(transitiveCallersOfIllegalMethod)
                .append(enclosingMethod)
                .anyMatch(caller -> {
                    MethodTree callerTree = ASTHelpers.findMethod(caller, state);
                    return Tasks.isTaskAction(callerTree, state) || Tasks.overridesExecute(callerTree, state);
                });
        if (isInvokedAtTaskExecution) {
            firstMatchingViolation.get().fixOrReport(tree, state);
        }

        return Description.NO_MATCH;
    }

    // Only the first matching violation is applied. Put more specific violations first.
    private final List<Violation> violations = List.of(new GetProjectGetLogger(this), new GetProject(this));

    @Override
    public MoreInfoLink moreInfoLink() {
        return new MoreInfoHeadingLink("adopting-the-configuration-cache.md", "Solving Configuration Cache problems");
    }
}
