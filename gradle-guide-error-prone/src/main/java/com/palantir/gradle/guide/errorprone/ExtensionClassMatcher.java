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

import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.suppliers.Suppliers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Type;
import java.util.Optional;

public interface ExtensionClassMatcher extends BugChecker.MethodInvocationTreeMatcher {

    Matcher<ExpressionTree> IS_EXTENSION_CREATE = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.plugins.ExtensionContainer")
            .named("create");
    Supplier<Type> CLASS_TYPE_SUPPLIER = Suppliers.typeFromString("java.lang.Class");

    default Optional<ClassSymbol> matchExtensionClass(MethodInvocationTree tree, VisitorState state) {
        // We have to do our checks on Extensions where they are created rather than on the Extension types themselves
        // This is because Extension types do not extend a class or implement an interface. There's no way to tell if
        // a certain class is an extension or not just looking at its type declaration alone.. Especially given
        // there are many other types with names ending in Extension, eg Junit 5 extensions.
        if (!IS_EXTENSION_CREATE.matches(tree, state)) {
            return Optional.empty();
        }

        // We need at least 2 arguments (name and class)
        if (tree.getArguments().size() < 2) {
            return Optional.empty();
        }

        // Get the second argument which should be the class type
        ExpressionTree classArg = tree.getArguments().get(1);
        return Optional.ofNullable(ASTHelpers.getType(classArg))
                .filter(argType -> ASTHelpers.isSubtype(argType, CLASS_TYPE_SUPPLIER.get(state), state))
                .filter(argType -> !argType.getTypeArguments().isEmpty())
                .map(argType -> argType.getTypeArguments().get(0))
                .filter(extensionType -> extensionType.tsym instanceof ClassSymbol)
                .map(extensionType -> (ClassSymbol) extensionType.tsym);
    }
}
