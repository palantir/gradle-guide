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

import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.errorprone.VisitorState;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.fixes.SuggestedFixes.AdditionPosition;
import com.google.errorprone.util.ASTHelpers;
import com.palantir.gradle.guide.errorprone.NonAbstractGradleType;
import com.palantir.gradle.guide.errorprone.utils.Tasks.TaskOrAction;
import com.palantir.gradle.guide.errorprone.utils.Tasks.TaskOrAction.Variant;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * An autofix for a Gradle task. This usually involves injecting a Gradle service, and replacing violating methods
 * with methods from the service.
 * @param servicesRequired The Gradle services required to make this fix
 * @param fixTemplate Replaces the violating chained call
 */
public record GradleFix(List<GradleService> servicesRequired, FixTemplate fixTemplate) {
    public static GradleFix of(GradleService service, FixTemplate fixedCall) {
        return new GradleFix(List.of(service), fixedCall);
    }

    public static GradleFix of(FixTemplate fixedCall) {
        return new GradleFix(List.of(), fixedCall);
    }

    public boolean requiresServiceInjection() {
        return !servicesRequired.isEmpty();
    }

    public record GradleFixContext(
            MethodInvocationTree illegalCallToReplace, int violatingChainLength, TaskOrAction enclosingClass) {}

    public SuggestedFix render(GradleFixContext context, VisitorState state) {
        Preconditions.checkArgument(
                context.enclosingClass.type() == Variant.TASK || !requiresServiceInjection(),
                "Only Tasks can be fixed with service injection");

        SuggestedFix.Builder fixBuilder = SuggestedFix.builder();

        // Preserve arguments (if any)
        String fixedCallChain;
        if (fixTemplate.numFormatArgs() == 1) {
            String args = context.illegalCallToReplace.getArguments().stream()
                    .map(state::getSourceForNode)
                    .collect(Collectors.joining(", "));
            fixedCallChain = fixTemplate.render(args);
        } else {
            fixedCallChain = fixTemplate.render();
        }

        // Preserve receiver
        MethodInvocationTree innermost = context.illegalCallToReplace;
        for (int i = 0; i < context.violatingChainLength - 1; ++i) {
            innermost = (MethodInvocationTree) ASTHelpers.getReceiver(innermost);
        }

        Optional<String> receiverSource =
                Optional.ofNullable(ASTHelpers.getReceiver(innermost)).map(state::getSourceForNode);
        String correctedCallWithReceiver =
                receiverSource.map(receiver -> receiver + ".").orElse("") + fixedCallChain;
        fixBuilder.replace(context.illegalCallToReplace, correctedCallWithReceiver);

        List<String> args = context.illegalCallToReplace.getArguments().stream()
                .map(state::getSourceForNode)
                .filter(Predicates.notNull())
                .toList();

        // Turn class abstract if we need to inject any services
        boolean isAlreadyAbstract =
                ASTHelpers.getSymbol(context.enclosingClass.tree()).isAbstract();
        if (requiresServiceInjection() && !isAlreadyAbstract) {
            NonAbstractGradleType.maybeTurnClassAbstract(context.enclosingClass.tree(), state)
                    .ifPresent(fixBuilder::merge);
        }

        // Inject services
        servicesRequired.stream()
                .filter(service -> !alreadyInjected(service, context.enclosingClass.tree(), state))
                .forEach(service -> {
                    fixBuilder.addImport(service.fullyQualifiedName());
                    fixBuilder.addImport("javax.inject.Inject");
                    SuggestedFix serviceGetter = SuggestedFixes.addMembers(
                            context.enclosingClass.tree(),
                            state,
                            AdditionPosition.FIRST,
                            String.format(
                                    "@Inject\nprotected abstract %s %s();", service.className(), service.getterName()));
                    fixBuilder.merge(serviceGetter);
                });

        return fixBuilder.build();
    }

    @SuppressWarnings("ASTHelpersSuggestions")
    private boolean alreadyInjected(GradleService service, ClassTree classTree, VisitorState state) {
        return ASTHelpers.getSymbol(classTree).getEnclosedElements().stream()
                .filter(symbol -> symbol instanceof MethodSymbol)
                .map(symbol -> (MethodSymbol) symbol)
                .filter(symbol -> symbol.getSimpleName().contentEquals(service.getterName()))
                .filter(Symbol::isAbstract)
                .anyMatch(symbol -> ASTHelpers.isSameType(symbol.getReturnType(), service.getType(state), state));
    }
}
