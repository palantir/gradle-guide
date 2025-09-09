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
import com.google.errorprone.util.ASTHelpers;
import com.palantir.gradle.guide.errorprone.utils.GradleManagedTypes;
import com.palantir.gradle.guide.errorprone.utils.Tasks;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@AutoService(BugChecker.class)
@BugPattern(severity = SeverityLevel.ERROR, summary = GradleTypesAsFields.SUMMARY)
public final class GradleTypesAsFields extends GradleGuideBugChecker
        implements BugChecker.VariableTreeMatcher, ExtensionClassMatcher {
    public static final String SUMMARY =
            "Do not declare Properties, FileCollections and other Gradle managed types as fields directly on Tasks or "
                    + "Extensions. Instead, declare an abstract getter method, e.g., 'public abstract Property<String> "
                    + "getFoo();'. This enables Gradle to inject the property implementation automatically, removes "
                    + "boilerplate, and supports the Groovy DSL (e.g. `foo = 3`).";

    @Override
    public Description matchVariable(VariableTree tree, VisitorState state) {
        if (!(state.getPath().getParentPath().getLeaf() instanceof ClassTree enclosingClass)) {
            return Description.NO_MATCH;
        }

        if (!Tasks.TASK.matches(enclosingClass, state)) {
            return Description.NO_MATCH;
        }

        Type varType = ASTHelpers.getType(tree);
        if (varType != null && GradleManagedTypes.isManagedType(varType, state)) {
            return describeMatch(tree);
        }

        return Description.NO_MATCH;
    }

    @Override
    public Description matchExtensionClass(
            ClassSymbol extensionClass, MethodInvocationTree extensionContainerCreateTree, VisitorState state) {
        String fieldNames = StreamSupport.stream(
                        extensionClass.members().getSymbols().spliterator(), false)
                .filter(memberSym -> memberSym instanceof VarSymbol)
                .map(memberSym -> (VarSymbol) memberSym)
                .filter(varSym -> varSym.owner.equals(extensionClass))
                .filter(varSym -> GradleManagedTypes.isManagedType(varSym.type, state))
                .map(varSym -> varSym.getSimpleName().toString())
                .collect(Collectors.joining(", "));

        if (!fieldNames.isEmpty()) {
            return buildDescription(extensionContainerCreateTree)
                    .setMessage("Within the " + extensionClass.getSimpleName()
                            + " extension, the following declared fields are not abstract: " + fieldNames + "\n"
                            + SUMMARY)
                    .build();
        }

        return Description.NO_MATCH;
    }

    @Override
    public MoreInfoLink moreInfoLink() {
        return new MoreInfoPageLink("managed-types-and-properties.md");
    }
}
