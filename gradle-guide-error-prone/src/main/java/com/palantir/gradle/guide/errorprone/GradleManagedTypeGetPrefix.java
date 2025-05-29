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
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.suppliers.Suppliers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.lang.model.element.Modifier;

@AutoService(BugChecker.class)
@BugPattern(severity = SeverityLevel.ERROR, summary = GradleManagedTypeGetPrefix.SUMMARY)
public final class GradleManagedTypeGetPrefix extends GradleGuideBugChecker
        implements BugChecker.MethodTreeMatcher, ExtensionClassMatcher {
    private static final Matcher<ClassTree> IS_TASK = Matchers.isSubtypeOf("org.gradle.api.Task");
    private static final Supplier<Type> PROVIDER_TYPE_SUPPLIER =
            Suppliers.typeFromString("org.gradle.api.provider.Provider");
    private static final Supplier<Type> DOMAIN_OBJECT_COLLECTION_TYPE_SUPPLIER =
            Suppliers.typeFromString("org.gradle.api.DomainObjectCollection");
    private static final Supplier<Type> FILE_COLLECTION_TYPE_SUPPLIER =
            Suppliers.typeFromString("org.gradle.api.file.FileCollection");

    public static final String SUMMARY =
            "Abstract methods in Tasks or Extensions that return Gradle managed types should start with 'get'. "
                    + "This allows Gradle to handle property injection correctly. For example, use "
                    + "'public abstract Property<String> getFoo();' instead of "
                    + "'public abstract Property<String> foo();'. This enables Gradle to inject the property "
                    + "implementation automatically, removes boilerplate, and supports the Groovy DSL "
                    + "(e.g. `foo = 3`).";

    @Override
    public Description matchMethod(MethodTree method, VisitorState state) {
        ClassTree enclosingClass = ASTHelpers.findEnclosingNode(state.getPath(), ClassTree.class);
        if (enclosingClass == null) {
            return Description.NO_MATCH;
        }

        if (!IS_TASK.matches(enclosingClass, state)) {
            return Description.NO_MATCH;
        }

        if (!isManagedPropertyType(ASTHelpers.getType(method.getReturnType()), state)) {
            return Description.NO_MATCH;
        }

        if (!isAbstract(ASTHelpers.getSymbol(method))) {
            return Description.NO_MATCH;
        }

        if (!method.getName().toString().startsWith("get")) {
            return describeMatch(method);
        }

        return Description.NO_MATCH;
    }

    @Override
    public Description matchExtensionClass(
            ClassSymbol extensionClass, MethodInvocationTree extensionContainerCreateTree, VisitorState state) {

        String methodNames = StreamSupport.stream(
                        extensionClass.members().getSymbols().spliterator(), false)
                .filter(memberSym -> memberSym instanceof MethodSymbol)
                .map(memberSym -> (MethodSymbol) memberSym)
                .filter(methodSym -> methodSym.owner.equals(extensionClass))
                .filter(GradleManagedTypeGetPrefix::isAbstract)
                .filter(methodSym -> isManagedPropertyType(methodSym.getReturnType(), state))
                .map(MethodSymbol::getSimpleName)
                .filter(simpleName -> !simpleName.toString().startsWith("get"))
                .map(Object::toString)
                .collect(Collectors.joining(", "));

        if (!methodNames.isEmpty()) {
            return buildDescription(extensionContainerCreateTree)
                    .setMessage("Within the " + extensionClass.getSimpleName()
                            + " extension, the following methods do not start with 'get': " + methodNames + "\n"
                            + SUMMARY)
                    .build();
        }

        return Description.NO_MATCH;
    }

    private static boolean isAbstract(Symbol symbol) {
        return symbol != null && symbol.getModifiers().contains(Modifier.ABSTRACT);
    }

    private static boolean isManagedPropertyType(Type type, VisitorState state) {
        return isSubtypeOfProvider(type, state)
                || isSubtypeOfDomainObjectCollection(type, state)
                || isSubtypeOfFileCollection(type, state);
    }

    private static boolean isSubtypeOfProvider(Type type, VisitorState state) {
        return ASTHelpers.isSubtype(type, PROVIDER_TYPE_SUPPLIER.get(state), state);
    }

    private static boolean isSubtypeOfDomainObjectCollection(Type type, VisitorState state) {
        return ASTHelpers.isSubtype(type, DOMAIN_OBJECT_COLLECTION_TYPE_SUPPLIER.get(state), state);
    }

    private static boolean isSubtypeOfFileCollection(Type type, VisitorState state) {
        return ASTHelpers.isSubtype(type, FILE_COLLECTION_TYPE_SUPPLIER.get(state), state);
    }

    @Override
    public MoreInfoLink moreInfoLink() {
        return new MoreInfoPageLink("managed-types-and-properties.md");
    }
}
