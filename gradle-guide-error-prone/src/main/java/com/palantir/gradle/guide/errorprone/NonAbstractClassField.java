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
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@AutoService(BugChecker.class)
@BugPattern(severity = SeverityLevel.ERROR, summary = NonAbstractClassField.SUMMARY)
public final class NonAbstractClassField extends GradleGuideBugChecker
        implements BugChecker.VariableTreeMatcher, BugChecker.MethodInvocationTreeMatcher {
    private static final Matcher<ClassTree> IS_TASK = Matchers.isSubtypeOf("org.gradle.api.Task");
    private static final Matcher<ExpressionTree> IS_EXTENSION_CREATE = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.plugins.ExtensionContainer")
            .named("create");
    private static final Supplier<Type> CLASS_TYPE_SUPPLIER = Suppliers.typeFromString("java.lang.Class");
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
        ClassTree enclosingClass = ASTHelpers.findEnclosingNode(state.getPath(), ClassTree.class);
        if (enclosingClass == null) {
            return Description.NO_MATCH;
        }

        if (!IS_TASK.matches(enclosingClass, state)) {
            return Description.NO_MATCH;
        }

        if (!enclosingClass.getMembers().contains(tree)) {
            return Description.NO_MATCH;
        }

        Type varType = ASTHelpers.getType(tree);
        if (varType != null && isManagedPropertyType(varType, state)) {
            return describeMatch(tree);
        }

        return Description.NO_MATCH;
    }

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        // We have to do our checks on Extensions where they are created rather than on the Extension types themselves
        // This is because Extension types do not extend a class or implement an interface. There's no way to tell if
        // a certain class is an extension or not just looking at its type declaration alone.. Especially given
        // there are many other types with names ending in Extension, eg Junit 5 extensions.
        if (!IS_EXTENSION_CREATE.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        // We need at least 2 arguments (name and class)
        if (tree.getArguments().size() < 2) {
            return Description.NO_MATCH;
        }

        // Get the second argument which should be the class type
        ExpressionTree classArg = tree.getArguments().get(1);

        Optional<Type> extensionTypeOpt = typeArgumentFromPossibleClassType(state, classArg);
        if (extensionTypeOpt.isEmpty()) {
            return Description.NO_MATCH;
        }
        Type extensionType = extensionTypeOpt.get();

        ClassSymbol extSym = (ClassSymbol) extensionType.tsym;

        String fieldNames = StreamSupport.stream(extSym.members().getSymbols().spliterator(), false)
                .filter(memberSym -> memberSym instanceof VarSymbol)
                .map(memberSym -> (VarSymbol) memberSym)
                .filter(varSym -> varSym.owner.equals(extSym))
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

    private static Optional<Type> typeArgumentFromPossibleClassType(VisitorState state, ExpressionTree classArg) {
        // From a possible Class<T> extract T
        return Optional.ofNullable(ASTHelpers.getType(classArg))
                .filter(argType -> ASTHelpers.isSubtype(argType, CLASS_TYPE_SUPPLIER.get(state), state))
                .filter(argType -> !argType.getTypeArguments().isEmpty())
                .map(argType -> argType.getTypeArguments().get(0))
                .filter(extensionType -> extensionType.tsym != null);
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
