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
import com.google.errorprone.VisitorState;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.fixes.SuggestedFixes.AdditionPosition;
import com.google.errorprone.util.ASTHelpers;
import com.palantir.gradle.guide.errorprone.NonAbstractGradleType;
import com.palantir.gradle.guide.errorprone.utils.Tasks.TaskOrAction.Variant;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.util.Name;
import java.util.Optional;
import javax.inject.Inject;

/**
 * A fix which injects a service.
 */
public abstract class ServiceBasedGradleFix implements GradleFix {

    /**
     * The service we have to inject for this fix.
     */
    protected abstract GradleService gradleService();

    /**
     * The fixed method call done on the service. Doesn't include the call to get the service itself.
     */
    protected abstract String fixedMethod();

    /**
     * Injects the service only if an abstract injected method for gradleService doesn't exist yet,
     * and builds a chain call with the injected method + fixedMethod.
     */
    @Override
    public SuggestedFix render(GradleFixContext context, VisitorState state) {
        Preconditions.checkArgument(
                context.enclosingClass().type() == Variant.TASK, "Only Tasks can be fixed with service injection");

        SuggestedFix.Builder fixBuilder = SuggestedFix.builder();

        GradleService service = gradleService();
        String getServiceMethodName = findServiceInjection(
                        service, context.enclosingClass().tree(), state)
                .orElseGet(() -> {
                    fixBuilder.addImport(service.fullyQualifiedName());
                    fixBuilder.addImport("javax.inject.Inject");
                    SuggestedFix serviceGetter = SuggestedFixes.addMembers(
                            context.enclosingClass().tree(),
                            state,
                            AdditionPosition.FIRST,
                            String.format(
                                    "@Inject\nprotected abstract %s %s();", service.className(), service.getterName()));
                    fixBuilder.merge(serviceGetter);
                    return service.getterName();
                });

        Replacement replacement;
        if (fixedMethod().contains("%s")) {
            replacement = Replacement.template(String.format("%s().%s", getServiceMethodName, fixedMethod()));
        } else {
            replacement = Replacement.literal(String.format("%s().%s", getServiceMethodName, fixedMethod()));
        }
        String fixedCallChain = replacement.render(context.illegalCallToReplace(), state);

        MethodInvocationTree innermost = context.illegalCallToReplace();
        for (int i = 0; i < context.violatingChainLength() - 1; ++i) {
            innermost = (MethodInvocationTree) ASTHelpers.getReceiver(innermost);
        }

        Optional<String> receiverSource =
                Optional.ofNullable(ASTHelpers.getReceiver(innermost)).map(state::getSourceForNode);
        String correctedCallWithReceiver =
                receiverSource.map(receiver -> receiver + ".").orElse("") + fixedCallChain;
        fixBuilder.replace(context.illegalCallToReplace(), correctedCallWithReceiver);

        boolean isAlreadyAbstract =
                ASTHelpers.getSymbol(context.enclosingClass().tree()).isAbstract();
        if (!isAlreadyAbstract) {
            NonAbstractGradleType.maybeTurnClassAbstract(
                            context.enclosingClass().tree(), state)
                    .ifPresent(fixBuilder::merge);
        }

        return fixBuilder.build();
    }

    /**
     * All ServiceBasedGradleFixes need service injection.
     */
    @Override
    public boolean requiresServiceInjection() {
        return true;
    }

    @SuppressWarnings("ASTHelpersSuggestions")
    private Optional<String> findServiceInjection(GradleService service, ClassTree classTree, VisitorState state) {
        return ASTHelpers.getSymbol(classTree).getEnclosedElements().stream()
                .filter(symbol -> symbol instanceof MethodSymbol)
                .map(symbol -> (MethodSymbol) symbol)
                .filter(symbol -> symbol.getAnnotation(Inject.class) != null)
                .filter(Symbol::isAbstract)
                .filter(symbol -> ASTHelpers.isSameType(symbol.getReturnType(), service.getType(state), state))
                .findAny()
                .map(Symbol::getSimpleName)
                .map(Name::toString);
    }
}
