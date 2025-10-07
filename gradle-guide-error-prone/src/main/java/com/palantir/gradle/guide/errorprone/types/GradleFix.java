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

package com.palantir.gradle.guide.errorprone.types;

import com.google.common.base.Preconditions;
import com.google.common.collect.Streams;
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
import com.sun.tools.javac.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Name;

/**
 * An autofix for a Gradle task. This usually involves injecting a Gradle service, and replacing violating methods
 * with methods from the service.
 * @param service The Gradle services required to make this fix
 * @param replacement Replaces the violating chained call
 */
public record GradleFix(Optional<GradleService> service, Replacement replacement) {
    /**
     * Create a fix which introduces a method call on the Gradle service.
     * <p>The service will be injected if no methods returning the service, or fields of that service type exist.
     *
     * <p>
     * Here, {@code FileSystemOperations} has been injected, as {@code getFileSystemOperations} to be used in the task
     * action body.
     *
     * <pre>
     * abstract class AlreadyAbstractTask extends DefaultTask {
     *     &#64;Inject
     *     protected abstract FileSystemOperations getFileSystemOperations();
     *
     *     &#64;TaskAction
     *     final void action() {
     *         getFileSystemOperations().copy(copySpec -> {
     *             copySpec.from("src");
     *             copySpec.into("dest");
     *         });
     *     }
     * }
     * </pre>
     */
    public static GradleFix onService(GradleService serviceUsedToFix, Replacement replacement) {
        return new GradleFix(Optional.of(serviceUsedToFix), replacement);
    }

    /**
     * Create a fix which introduces a method call on {@code this}, and doesn't require any service injection.
     * <p>This method is used when the fix can be applied directly to the current class instance without
     * needing to inject additional Gradle services. The replacement will be applied using existing
     * methods or properties available on the current object.
     *
     * <p>
     * Here, {@code getLogger} is a method on {@code this}, via {@code DefaultTask}:
     *
     * <pre>
     * public class ExistingTask extends DefaultTask {
     *     &#64;TaskAction
     *     public void action() {
     *         // Fix applied directly on 'this' without service injection
     *         getLogger().info("Task execution started");
     *     }
     * }
     * </pre>
     *
     * @param replacement the code replacement to apply on the current instance
     * @return a {@code GradleFix} that operates on the current object without service injection
     * @see #onService(GradleService, Replacement) for fixes requiring service injection
     */
    public static GradleFix onThis(Replacement replacement) {
        return new GradleFix(Optional.empty(), replacement);
    }

    public boolean requiresServiceInjection() {
        return service.isPresent();
    }

    public record GradleFixContext(
            MethodInvocationTree illegalCallToReplace, int violatingChainLength, TaskOrAction enclosingClass) {}

    private static String chain(String expr1, String expr2) {
        if (expr1.isEmpty()) {
            return expr2;
        } else if (expr2.isEmpty()) {
            return expr1;
        } else {
            return expr1 + "." + expr2;
        }
    }

    public SuggestedFix render(GradleFixContext context, VisitorState state) {
        Preconditions.checkArgument(
                context.enclosingClass().type() == Variant.TASK || !requiresServiceInjection(),
                "Only Tasks can be fixed with service injection");

        SuggestedFix.Builder fixBuilder = SuggestedFix.builder();

        // Either the service method (getFileSystemOperations()) or service field (fileSystemOperations)
        String serviceReceiver = getServiceReceiver(context, state, fixBuilder);

        // Preserve arguments (if any)
        String fixedCallChain = chain(serviceReceiver, replacement.render(context.illegalCallToReplace, state));

        // Preserve receiver
        MethodInvocationTree innermost = context.illegalCallToReplace;
        for (int i = 0; i < context.violatingChainLength - 1; ++i) {
            innermost = (MethodInvocationTree) ASTHelpers.getReceiver(innermost);
        }

        Optional<String> receiverSource =
                Optional.ofNullable(ASTHelpers.getReceiver(innermost)).map(state::getSourceForNode);
        String correctedCallWithReceiver =
                receiverSource.map(recv -> recv + ".").orElse("") + fixedCallChain;
        fixBuilder.replace(context.illegalCallToReplace, correctedCallWithReceiver);

        // Turn class abstract if we need to inject any services
        boolean isAlreadyAbstract =
                ASTHelpers.getSymbol(context.enclosingClass().tree()).isAbstract();
        if (requiresServiceInjection() && !isAlreadyAbstract) {
            NonAbstractGradleType.maybeTurnClassAbstract(
                            context.enclosingClass().tree(), state)
                    .ifPresent(fixBuilder::merge);
        }
        return fixBuilder.build();
    }

    private String getServiceReceiver(GradleFixContext context, VisitorState state, SuggestedFix.Builder fixBuilder) {
        if (service.isEmpty()) {
            // This means the method is on DefaultTask (i.e. `this`). No receiver needed.
            return "";
        }
        GradleService gradleService = service.get();
        Optional<String> serviceReceiverMaybe =
                findServiceReceiver(gradleService, context.enclosingClass().tree(), state);
        if (serviceReceiverMaybe.isPresent()) {
            return serviceReceiverMaybe.get();
        }

        injectService(context, state, gradleService, fixBuilder);
        return gradleService.defaultGetterName() + "()";
    }

    private static void injectService(
            GradleFixContext context, VisitorState state, GradleService service, SuggestedFix.Builder fixBuilder) {
        fixBuilder.addImport(service.fullyQualifiedName());
        fixBuilder.addImport("javax.inject.Inject");
        SuggestedFix serviceGetter = SuggestedFixes.addMembers(
                context.enclosingClass().tree(),
                state,
                AdditionPosition.FIRST,
                String.format(
                        "@Inject\nprotected abstract %s %s();", service.className(), service.defaultGetterName()));
        fixBuilder.merge(serviceGetter);
    }

    @SuppressWarnings("ASTHelpersSuggestions")
    private Optional<String> findServiceReceiver(GradleService gradleService, ClassTree classTree, VisitorState state) {
        List<Symbol> enclosedElements = ASTHelpers.getSymbol(classTree).getEnclosedElements();
        Stream<String> fields = enclosedElements.stream()
                .filter(symbol -> symbol.getKind().equals(ElementKind.FIELD))
                .filter(symbol -> ASTHelpers.isSameType(symbol.asType(), gradleService.getType(state), state))
                .map(Symbol::getSimpleName)
                .map(Name::toString);
        Stream<String> methodsReturningService = ASTHelpers.getSymbol(classTree).getEnclosedElements().stream()
                .filter(symbol -> symbol instanceof MethodSymbol)
                .map(symbol -> (MethodSymbol) symbol)
                .filter(symbol -> ASTHelpers.isSameType(symbol.getReturnType(), gradleService.getType(state), state))
                .map(symbol -> symbol.getSimpleName() + "()");
        return Streams.concat(fields, methodsReturningService).findFirst();
    }
}