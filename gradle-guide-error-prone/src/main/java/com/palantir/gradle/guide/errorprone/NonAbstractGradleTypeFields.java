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
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@AutoService(BugChecker.class)
@BugPattern(severity = SeverityLevel.ERROR, summary = NonAbstractGradleTypeFields.SUMMARY)
public final class NonAbstractGradleTypeFields extends GradleGuideBugChecker
        implements BugChecker.VariableTreeMatcher, ExtensionClassMatcher {
    private static final Matcher<ClassTree> IS_TASK = Matchers.isSubtypeOf("org.gradle.api.Task");
    private static final Supplier<Type> PROVIDER_TYPE_SUPPLIER =
            Suppliers.typeFromString("org.gradle.api.provider.Provider");
    private static final Supplier<Type> DOMAIN_OBJECT_COLLECTION_TYPE_SUPPLIER =
            Suppliers.typeFromString("org.gradle.api.DomainObjectCollection");
    private static final Supplier<Type> FILE_COLLECTION_TYPE_SUPPLIER =
            Suppliers.typeFromString("org.gradle.api.file.FileCollection");

    public static final String SUMMARY =
            "Do not declare class fields directly on Tasks or Extensions. Instead, declare an abstract getter "
                    + "method, e.g., 'public abstract Property<String> getFoo();'. This enables Gradle to inject the "
                    + "property implementation automatically, removes boilerplate, and supports the Groovy DSL "
                    + "(e.g. `foo = 3`).";

    @Override
    public Description matchVariable(VariableTree tree, VisitorState state) {
        if (!(state.getPath().getParentPath().getLeaf() instanceof ClassTree enclosingClass)) {
            return Description.NO_MATCH;
        }

        if (!IS_TASK.matches(enclosingClass, state)) {
            return Description.NO_MATCH;
        }

        Type varType = ASTHelpers.getType(tree);
        if (varType != null && isManagedPropertyType(varType, state)) {
            return describeMatch(tree);
        }

        return Description.NO_MATCH;
    }

    @Override
    public Description matchExtensionClass(ClassSymbol extensionClass, MethodInvocationTree tree, VisitorState state) {
        String fieldNames = StreamSupport.stream(
                        extensionClass.members().getSymbols().spliterator(), false)
                .filter(memberSym -> memberSym instanceof VarSymbol)
                .map(memberSym -> (VarSymbol) memberSym)
                .filter(varSym -> varSym.owner.equals(extensionClass))
                .filter(varSym -> isManagedPropertyType(varSym.type, state))
                .map(varSym -> varSym.getSimpleName().toString())
                .collect(Collectors.joining(", "));

        if (!fieldNames.isEmpty()) {
            return buildDescription(tree)
                    .setMessage(SUMMARY + "\n Declared fields: " + fieldNames + "\n")
                    .build();
        }

        return Description.NO_MATCH;
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
