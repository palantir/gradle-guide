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
import com.sun.source.tree.Tree.Kind;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Type;
import java.util.Optional;
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
        implements BugChecker.ClassTreeMatcher, ExtensionClassMatcher {
    private static final Matcher<ClassTree> IS_TASK = Matchers.isSubtypeOf("org.gradle.api.Task");
    private static final Supplier<Type> CLASS_TYPE_SUPPLIER = Suppliers.typeFromString("java.lang.Class");

    @Override
    public Description matchClass(ClassTree tree, VisitorState state) {
        if (tree.getKind().equals(Kind.INTERFACE)) {
            return Description.NO_MATCH;
        }

        if (tree.getModifiers().getFlags().contains(Modifier.ABSTRACT)) {
            return Description.NO_MATCH;
        }

        if (IS_TASK.matches(tree, state)) {
            return buildDescription(tree).build();
        }

        return Description.NO_MATCH;
    }

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        return matchExtensionClass(tree, state)
                .filter(extSym -> {
                    Type type = extSym.type;
                    return !(isTypeAbstract(type) || type.isInterface());
                })
                .map(nonAbstractType -> buildDescription(tree).build())
                .orElse(Description.NO_MATCH);
    }

    private static Optional<Type> typeArgumentFromPossibleClassType(VisitorState state, ExpressionTree classArg) {
        // From a possible Class<T> extract T
        return Optional.ofNullable(ASTHelpers.getType(classArg))
                .filter(argType -> ASTHelpers.isSubtype(argType, CLASS_TYPE_SUPPLIER.get(state), state))
                .filter(argType -> !argType.getTypeArguments().isEmpty())
                .map(argType -> argType.getTypeArguments().get(0))
                .filter(extensionType -> extensionType.tsym != null);
    }

    private static boolean isTypeAbstract(Type type) {
        return (type.tsym.flags() & Flags.ABSTRACT) != 0;
    }

    @Override
    public MoreInfoLink moreInfoLink() {
        return new MoreInfoPageLink("managed-types-and-properties.md");
    }
}
