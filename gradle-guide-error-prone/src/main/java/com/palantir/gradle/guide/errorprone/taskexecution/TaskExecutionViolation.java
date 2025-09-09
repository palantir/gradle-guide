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

import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.palantir.gradle.guide.errorprone.taskexecution.GradleFix.GradleFixContext;
import com.palantir.gradle.guide.errorprone.utils.MethodCallGraph;
import com.palantir.gradle.guide.errorprone.utils.Tasks;
import com.palantir.gradle.guide.errorprone.utils.Tasks.TaskOrAction;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import java.util.Optional;
import java.util.Set;
import one.util.streamex.StreamEx;

/**
 * Represents strategies to report or auto-fix illegal methods and methods chained to it.
 */
public record TaskExecutionViolation(ChainedCall violation, String message, Optional<GradleFix> fix)
        implements Comparable<TaskExecutionViolation> {

    public static TaskExecutionViolation fix(ChainedCall violation, String message, GradleFix fix) {
        return new TaskExecutionViolation(violation, message, Optional.of(fix));
    }

    public static TaskExecutionViolation warn(ChainedCall violation, String message) {
        return new TaskExecutionViolation(violation, message, Optional.empty());
    }

    @Override
    public int compareTo(TaskExecutionViolation o) {
        // More specific violations come before less specific violations
        return this.violation.chainLength() - o.violation.chainLength();
    }

    /**
     * Returns {@code true} if a fix or report was done for {@code illegalCall}.
     * Note that this is called on every method invocation, so performance matters.
     */
    public boolean fixOrReport(MethodInvocationTree illegalCall, VisitorState state, BugChecker bugChecker) {
        boolean callMatchesViolation = violation.matches(illegalCall, state);
        if (!callMatchesViolation) {
            return false; // Prune most true negatives
        }

        // Now, we have to prune cases where illegalCall matches the violating chained call,
        // but are done outside of execution time.
        // There will only be a few such cases. So, we now use the (quite expensive) method call graph.
        CompilationUnitTree compilationUnit = state.getPath().getCompilationUnit();
        MethodCallGraph callGraph = MethodCallGraph.getOrBuild(compilationUnit);

        Optional<MethodTree> enclosingMethodMaybe = Optional.ofNullable(ASTHelpers.findEnclosingMethod(state));
        if (enclosingMethodMaybe.isEmpty()) {
            return false; // We can't do anything here
        }
        MethodSymbol enclosingMethod = ASTHelpers.getSymbol(enclosingMethodMaybe.get());

        // Find transitive callers of enclosing method first, as the illegal method e.g. getProject() is defined in
        // Gradle source, not the source file this BugChecker is running on.
        // If we included methods defined externally into the illegalCall graph,
        // IllegalMethodCalledDuringTaskExecutionTest#getProject_in_constructor_ok will fail.
        Set<MethodSymbol> transitiveCallersOfIllegalMethod = callGraph.transitiveCallers(enclosingMethod);

        boolean isInvokedAtTaskExecution = StreamEx.of(transitiveCallersOfIllegalMethod)
                .append(enclosingMethod)
                .anyMatch(caller -> {
                    MethodTree callerTree = ASTHelpers.findMethod(caller, state);
                    return Tasks.isTaskAction(callerTree, state) || Tasks.overridesExecute(callerTree, state);
                });
        if (!isInvokedAtTaskExecution) {
            // We're not sure whether `illegalCall` is done during task execution.
            // So let's be conservative.
            return false;
        }

        Optional<TaskOrAction> taskOrActionMaybe = Tasks.findFirstEnclosingTaskOrAction(state.getPath(), state);
        if (taskOrActionMaybe.isEmpty()) {
            return false; // We can't do anything here
        }

        TaskOrAction taskOrAction = taskOrActionMaybe.get();
        Description.Builder descriptionBuilder =
                bugChecker.buildDescription(illegalCall).setMessage(message);

        GradleFixContext context = new GradleFixContext(illegalCall, violation.chainLength(), taskOrAction);
        switch (taskOrAction.type()) {
            case ACTION:
                // We can't inject stuff into `Action<Task>`s, as `Action`s can't be made abstract
                boolean canFix = fix.map(gradleFix -> !gradleFix.requiresServiceInjection())
                        .orElse(false);
                if (canFix) {
                    descriptionBuilder.addFix(fix.get().render(context, state));
                }
                break;
            case TASK:
                fix.ifPresent(gradleFix -> descriptionBuilder.addFix(gradleFix.render(context, state)));
        }

        state.reportMatch(descriptionBuilder.build());
        return true;
    }
}
