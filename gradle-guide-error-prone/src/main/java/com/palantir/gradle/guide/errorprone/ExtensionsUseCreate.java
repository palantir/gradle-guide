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
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import java.util.List;
import java.util.Optional;

@AutoService(BugChecker.class)
@BugPattern(
        summary =
                "Gradle extensions should be registered using `create`, not by constructing and passing a new instance to `add`.",
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
            String calledName = state.getSourceForNode(methodArg.getMethodSelect());
            if (calledName == null
                    || calledName.endsWith(".getByName")
                    || calledName.endsWith(".findByName")
                    || calledName.endsWith(".findByType")) {
                return Description.NO_MATCH;
            }
        }

        if (instanceArg instanceof NewClassTree newClass) {
            return buildDescription(tree)
                    .setMessage(
                            "Register extensions with `create`, not by constructing and passing a new instance to `add`.")
                    .build();
        }

        if (instanceArg instanceof IdentifierTree ident) {
            Optional<VariableTree> varDecl = findVariableDeclaration(ident, state);
            if (varDecl.isPresent() && varDecl.get().getInitializer() instanceof NewClassTree newClass) {
                return buildDescription(tree)
                        .setMessage(
                                "Register extensions with `create`, not by constructing and passing a new instance to `add`.")
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
        // Walk up enclosing scope to find declaration using streams
        for (TreePath path = state.getPath(); path != null; path = path.getParentPath()) {
            if (path.getLeaf() instanceof BlockTree block) {
                return block.getStatements().stream()
                        .filter(stmt -> stmt instanceof VariableTree)
                        .map(stmt -> (VariableTree) stmt)
                        .filter(vt -> ASTHelpers.getSymbol(vt).equals(varSym))
                        .findFirst();
            }
        }
        return Optional.empty();
    }

    @Override
    public MoreInfoLink moreInfoLink() {
        return new MoreInfoPageLink("extensions-use-create.md");
    }
}
