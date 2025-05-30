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
import com.google.errorprone.util.ASTHelpers;
import com.palantir.gradle.guide.errorprone.utils.ReplacementTracker.SuggestedFixBuilder;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AutoService(BugChecker.class)
@BugPattern(
        summary = "Gradle extensions should be registered using `create` rather than by constructing and passing a new "
                + "instance to `add`. `create` ensures that Gradle manages the extension's lifecycle, type, "
                + "and dependency injection correctly, while `add` with a manually constructed instance can "
                + "lead to misconfigured extensions and bypasses Gradle's internal management.",
        severity = SeverityLevel.ERROR)
public final class ExtensionsUseCreate extends GradleGuideBugChecker implements BugChecker.MethodInvocationTreeMatcher {

    private static final Matcher<ExpressionTree> IS_EXTENSION_ADD = Matchers.instanceMethod()
            .onDescendantOf("org.gradle.api.plugins.ExtensionContainer")
            .named("add");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
        if (!IS_EXTENSION_ADD.matches(tree, state)) {
            return Description.NO_MATCH;
        }

        List<? extends ExpressionTree> args = tree.getArguments();
        if (args.size() != 2 && args.size() != 3) {
            return Description.NO_MATCH;
        }

        ExpressionTree instanceArg = args.get(args.size() - 1);

        if (instanceArg instanceof MethodInvocationTree methodArg) {
            if (isIgnoredMethodInvocation(methodArg, state)) {
                return Description.NO_MATCH;
            }
        }

        if (instanceArg instanceof NewClassTree newClass) {
            return buildDescription(tree)
                    .addFix(suggestCreateFix(tree, newClass, Optional.empty(), state)
                            .build())
                    .build();
        }

        if (instanceArg instanceof IdentifierTree ident) {
            Optional<VariableTree> varDecl = findVariableDeclaration(ident, state);
            if (varDecl.isPresent() && varDecl.get().getInitializer() instanceof NewClassTree newClass) {
                return buildDescription(tree)
                        .addFix(suggestCreateFix(tree, newClass, varDecl, state).build())
                        .build();
            }
        }

        return Description.NO_MATCH;
    }

    private static Optional<VariableTree> findVariableDeclaration(IdentifierTree ident, VisitorState state) {
        Symbol sym = ASTHelpers.getSymbol(ident);
        if (!(sym instanceof VarSymbol varSym)) {
            return Optional.empty();
        }
        // Stream over the TreePath ancestry, filter for BlockTree, then stream their statements
        return Stream.iterate(state.getPath(), Objects::nonNull, TreePath::getParentPath)
                .map(TreePath::getLeaf)
                .filter(BlockTree.class::isInstance)
                .map(BlockTree.class::cast)
                .flatMap(block -> block.getStatements().stream())
                .filter(VariableTree.class::isInstance)
                .map(VariableTree.class::cast)
                .filter(vt -> ASTHelpers.getSymbol(vt).equals(varSym))
                .findFirst();
    }

    private static SuggestedFixBuilder suggestCreateFix(
            MethodInvocationTree addCall,
            NewClassTree newClass,
            Optional<VariableTree> maybeVarDecl,
            VisitorState state) {

        String extensionsReceiver = Optional.ofNullable(state.getSourceForNode(addCall.getMethodSelect()))
                .orElse("/* project.getExtensions() */")
                .replaceFirst("\\.add$", "");

        Type extType = ASTHelpers.getType(newClass);
        String extClass = extType == null ? "/* ExtensionType.class */" : extType.tsym.getSimpleName() + ".class";

        List<? extends ExpressionTree> addArgs = addCall.getArguments();
        String extName = state.getSourceForNode(addArgs.get(addArgs.size() == 3 ? 1 : 0));

        String constructorArgs =
                newClass.getArguments().stream().map(state::getSourceForNode).collect(Collectors.joining(", "));

        String createCall = Stream.of(extName, extClass, constructorArgs)
                .filter(Objects::nonNull)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", ", extensionsReceiver + ".create(", ")"));

        SuggestedFixBuilder fix = new SuggestedFixBuilder();
        fix.replace(addCall, createCall);
        maybeVarDecl.ifPresent(fix::delete);
        return fix;
    }

    private boolean isIgnoredMethodInvocation(MethodInvocationTree methodArg, VisitorState state) {
        String calledName = state.getSourceForNode(methodArg.getMethodSelect());
        return calledName == null
                || calledName.endsWith(".getByName")
                || calledName.endsWith(".findByName")
                || calledName.endsWith(".findByType");
    }

    @Override
    public MoreInfoLink moreInfoLink() {
        return new MoreInfoPageLink("managed-types-and-properties.md");
    }
}
