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
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.suppliers.Suppliers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.StreamSupport;
import javax.lang.model.element.Modifier;

@AutoService(BugChecker.class)
@BugPattern(
        severity = SeverityLevel.ERROR,
        summary = "When defining a custom Task or Extension, you should make it an abstract class "
                + "with abstract getter methods of each of the properties and other "
                + "gradle containers (eg NamedDomainObjectSet). Gradle will then automatically "
                + "create the properties and containers, removing a lot of boilerplate. Additionally, "
                + "as you declare eg `public abstract Property<Integer> getFoo()`, this will automatically "
                + "make the `foo = 3` groovy syntax work of the box.")
public final class NonAbstractGradleType extends GradleGuideBugChecker
        implements BugChecker.ClassTreeMatcher,
                BugChecker.MethodInvocationTreeMatcher,
                BugChecker.MethodTreeMatcher,
                BugChecker.VariableTreeMatcher {
    private static final Matcher<ClassTree> IS_TASK = Matchers.isSubtypeOf("org.gradle.api.Task");
    private static final Matcher<ExpressionTree> IS_EXTENSION_CREATE = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.plugins.ExtensionContainer")
            .named("create");
    private static final Supplier<Type> CLASS_TYPE_SUPPLIER = Suppliers.typeFromString("java.lang.Class");
    private static final Supplier<Type> PROVIDER_TYPE_SUPPLIER =
            Suppliers.typeFromString("org.gradle.api.provider.Provider");

    private static final String ABSTRACT_PROPERTY_METHOD_MSG = "Properties on Tasks or Extensions should be declared "
            + "abstract. Declare this method as 'public abstract', e.g., 'public abstract Property<Integer> "
            + "getFoo();'. This enables Gradle to inject the property implementation automatically, removing "
            + "boilerplate and supporting the Groovy DSL , this will automatically this will make the `foo = 3` groovy "
            + "syntax work of the box.";

    private static final String PROPERTY_GETTER_NAMING_MSG =
            "Properties on Tasks or Extensions should be named starting with 'get', e.g., 'getFoo'. This naming "
                    + "convention is required for Gradle to recognize and manage the property, enabling automatic "
                    + "property wiring and Groovy DSL support, this will automatically this will make the `foo = 3` "
                    + "groovy syntax work of the box.";

    private static final String PROPERTY_FIELD_MSG = "Do not declare Property fields directly on Tasks or Extensions. "
            + "Instead, declare an abstract getter method, e.g., 'public abstract Property<String> getFoo();'. This "
            + "enables Gradle to inject the property implementation automatically, removes boilerplate, and supports "
            + "the Groovy DSL (e.g. `foo = 3`).";

    @Override
    public Description matchClass(ClassTree tree, VisitorState state) {
        if (tree.getKind().equals(Kind.INTERFACE)) {
            return Description.NO_MATCH;
        }

        if (isAbstract(ASTHelpers.getSymbol(tree))) {
            return Description.NO_MATCH;
        }

        if (IS_TASK.matches(tree, state)) {
            return describeMatch(tree);
        }

        return Description.NO_MATCH;
    }

    @Override
    public Description matchMethod(MethodTree method, VisitorState state) {
        ClassTree enclosingClass = ASTHelpers.findEnclosingNode(state.getPath(), ClassTree.class);
        if (enclosingClass == null) {
            return Description.NO_MATCH;
        }

        if (!IS_TASK.matches(enclosingClass, state)) {
            return Description.NO_MATCH;
        }

        if (!isManagedPropertyGetter(method, state)) {
            return Description.NO_MATCH;
        }

        if (!isAbstract(ASTHelpers.getSymbol(method))) {
            return buildDescription(method)
                    .setMessage(ABSTRACT_PROPERTY_METHOD_MSG)
                    .build();
        }
        if (!method.getName().toString().startsWith("get")) {
            return buildDescription(method)
                    .setMessage(PROPERTY_GETTER_NAMING_MSG)
                    .build();
        }
        return Description.NO_MATCH;
    }

    @Override
    public Description matchVariable(VariableTree tree, VisitorState state) {
        ClassTree enclosingClass = ASTHelpers.findEnclosingNode(state.getPath(), ClassTree.class);
        if (enclosingClass == null) {
            return Description.NO_MATCH;
        }

        if (!IS_TASK.matches(enclosingClass, state)) {
            return Description.NO_MATCH;
        }

        Type varType = ASTHelpers.getType(tree);
        if (varType != null && isSubtypeOfProvider(varType, state)) {
            return buildDescription(tree).setMessage(PROPERTY_FIELD_MSG).build();
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

        if (!(isAbstract(extensionType.tsym) || extensionType.isInterface())) {
            return describeMatch(tree);
        }

        ClassSymbol extSym = (ClassSymbol) extensionType.tsym;
        if (hasPropertyField(extSym, state)) {
            return buildDescription(tree).setMessage(PROPERTY_FIELD_MSG).build();
        }

        return checkManagedPropertyGetters(extSym, tree, state);
    }

    private static boolean hasPropertyField(ClassSymbol extSym, VisitorState state) {
        return StreamSupport.stream(extSym.members().getSymbols().spliterator(), false)
                .filter(memberSym -> memberSym instanceof VarSymbol)
                .map(memberSym -> (VarSymbol) memberSym)
                .anyMatch(varSym -> isSubtypeOfProvider(varSym.type, state));
    }

    private Description checkManagedPropertyGetters(ClassSymbol extSym, MethodInvocationTree tree, VisitorState state) {
        return StreamSupport.stream(extSym.members().getSymbols().spliterator(), false)
                .filter(memberSym -> memberSym instanceof MethodSymbol)
                .map(memberSym -> (MethodSymbol) memberSym)
                .filter(memberSym -> isManagedPropertyGetter(memberSym, state))
                .map(memberSym -> {
                    if (!isAbstract(memberSym)) {
                        return buildDescription(tree)
                                .setMessage(ABSTRACT_PROPERTY_METHOD_MSG)
                                .build();
                    }
                    if (!memberSym.getSimpleName().toString().startsWith("get")) {
                        return buildDescription(tree)
                                .setMessage(PROPERTY_GETTER_NAMING_MSG)
                                .build();
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(Description.NO_MATCH);
    }

    private static Optional<Type> typeArgumentFromPossibleClassType(VisitorState state, ExpressionTree classArg) {
        return Optional.ofNullable(ASTHelpers.getType(classArg))
                .filter(argType -> ASTHelpers.isSubtype(argType, CLASS_TYPE_SUPPLIER.get(state), state))
                .filter(argType -> !argType.getTypeArguments().isEmpty())
                .map(argType -> argType.getTypeArguments().get(0))
                .filter(extensionType -> extensionType.tsym != null);
    }

    private static boolean isAbstract(Symbol symbol) {
        return symbol != null && symbol.getModifiers().contains(Modifier.ABSTRACT);
    }

    private static boolean isManagedPropertyGetter(MethodTree method, VisitorState state) {
        if (!method.getParameters().isEmpty()) {
            return false;
        }
        Type returnType = ASTHelpers.getType(method.getReturnType());
        return returnType != null && isSubtypeOfProvider(returnType, state);
    }

    private static boolean isManagedPropertyGetter(MethodSymbol method, VisitorState state) {
        if (!method.getParameters().isEmpty()) {
            return false;
        }
        Type returnType = method.getReturnType();
        return returnType != null && isSubtypeOfProvider(returnType, state);
    }

    private static boolean isSubtypeOfProvider(Type type, VisitorState state) {
        return ASTHelpers.isSubtype(type, PROVIDER_TYPE_SUPPLIER.get(state), state);
    }

    @Override
    public MoreInfoLink moreInfoLink() {
        return new MoreInfoPageLink("managed-types-and-properties.md");
    }
}
