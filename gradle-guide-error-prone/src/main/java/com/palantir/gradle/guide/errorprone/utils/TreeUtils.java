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

package com.palantir.gradle.guide.errorprone.utils;

import com.google.errorprone.VisitorState;
import com.google.errorprone.util.ASTHelpers;
import com.google.errorprone.util.FindIdentifiers;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.Type.WildcardType;
import com.sun.tools.javac.tree.JCTree;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class TreeUtils {
    public static int startPosition(Tree tree) {
        return ((JCTree) tree).getStartPosition();
    }

    public static Stream<TreePath> pathToRoot(TreePath start) {
        return Stream.iterate(start, path -> path.getParentPath() != null, TreePath::getParentPath);
    }

    // candidate must be in camel case
    private static Optional<String> tryConcatenations(Set<String> variablesInScope, String candidate) {
        String[] suffixes = {
            "Value", "Inner", "Item", "Data",
        };

        for (String suffix : suffixes) {
            if (!variablesInScope.contains(candidate + suffix)) {
                return Optional.of(candidate + suffix);
            }
        }

        return Optional.empty();
    }

    public static String sensibleLambdaParameterName(
            VisitorState state, ExpressionTree provider, Set<String> newlyAddedNames) {
        Stream<String> variablesInScopeOfOldCode =
                FindIdentifiers.findAllIdents(state).stream().map(varSymbol -> varSymbol.name.toString());
        Set<String> namesToAvoid = Stream.concat(variablesInScopeOfOldCode, newlyAddedNames.stream())
                .collect(Collectors.toSet());

        // Prefer using concatenations of the provider's variable name
        if (provider instanceof IdentifierTree providerIdentifier) {
            String providerVariable = providerIdentifier.getName().toString(); // already in camelCase
            Optional<String> triedConcatenations = tryConcatenations(namesToAvoid, providerVariable);
            if (triedConcatenations.isPresent()) {
                return triedConcatenations.get();
            }
        }

        Optional<Type> providerType = Optional.ofNullable(ASTHelpers.getType(provider));
        Optional<String> providerInnerType =
                providerType.flatMap(TreeUtils::getFirstTypeArgument).flatMap(TreeUtils::parseSimpleOrExtends);

        // Then, try to use the provider's type parameter. Most providers should have their type information intact.
        if (providerInnerType.isPresent()) {
            String innerType = pascalToCamelCase(providerInnerType.get());
            if (!namesToAvoid.contains(innerType)) {
                return innerType;
            }

            Optional<String> triedConcatenations = tryConcatenations(namesToAvoid, innerType);
            if (triedConcatenations.isPresent()) {
                return triedConcatenations.get();
            }
        }

        // Then, try to use the provider's type. Our last hope.
        if (providerType.isPresent()) {
            String providerTypeStr =
                    pascalToCamelCase(providerType.get().tsym.getSimpleName().toString());
            Optional<String> triedConcatenations = tryConcatenations(namesToAvoid, providerTypeStr);
            if (triedConcatenations.isPresent()) {
                return triedConcatenations.get();
            }
        }

        // Then, we give up.
        int suffix = 1;
        String giveUp = expressionToIdentifier(state, provider);
        while (namesToAvoid.contains(giveUp)) {
            giveUp = giveUp + (suffix++);
        }
        return giveUp;
    }

    private static String pascalToCamelCase(String pascalCase) {
        if (pascalCase.length() <= 1) {
            return pascalCase.toLowerCase();
        }
        return pascalCase.substring(0, 1).toLowerCase() + pascalCase.substring(1);
    }

    // SimpleType                       --> SimpleType
    // ? extends SimpleType             --> SimpleType
    // ?                                --> Optional.empty()
    // ? extends <? extends SimpleType> --> Optional.empty()
    // ? super SimpleType               --> Optional.empty()
    private static Optional<String> parseSimpleOrExtends(Type type) {
        if (type instanceof WildcardType wildcardType) {
            if (wildcardType.isExtendsBound() && !(wildcardType.getExtendsBound() instanceof WildcardType)) {
                type = wildcardType.getExtendsBound();
            } else {
                return Optional.empty();
            }
        }

        return Optional.of(type.tsym.getSimpleName().toString());
    }

    public static String expressionToIdentifier(VisitorState state, ExpressionTree expressionTree) {
        String originalSource = state.getSourceForNode(expressionTree);

        StringBuilder stringBuilder = new StringBuilder();

        char firstChar = originalSource.charAt(0);

        if (Character.isJavaIdentifierStart(firstChar)) {
            stringBuilder.append(firstChar);
        }

        boolean previousCharValid = true;

        for (int i = 1; i < originalSource.length(); i++) {
            char ch = originalSource.charAt(i);
            boolean validChar = Character.isJavaIdentifierPart(ch);
            if (validChar) {
                if (previousCharValid) {
                    stringBuilder.append(ch);
                } else {
                    stringBuilder.append(String.valueOf(ch).toUpperCase(Locale.ROOT));
                }
            }
            previousCharValid = validChar;
        }

        return stringBuilder.toString();
    }

    public static Optional<TreePath> findEnclosingBlock(TreePath path) {
        while (path != null) {
            Tree leaf = path.getLeaf();
            if (leaf instanceof BlockTree) {
                return Optional.of(path);
            }
            path = path.getParentPath();
        }
        return Optional.empty();
    }

    public static TreePath getFullCallChain(TreePath methodInvocationPath) {
        TreePath current = methodInvocationPath;

        while (true) {
            TreePath parent = current.getParentPath();
            if (parent == null) {
                break;
            }

            Tree parentLeaf = parent.getLeaf();

            // If parent is a MemberSelectTree and we are its expression, check grandparent
            if (parentLeaf instanceof MemberSelectTree memberSelect) {
                if (memberSelect.getExpression().equals(current.getLeaf())) {
                    TreePath grandParent = parent.getParentPath();
                    // If grandparent is a MethodInvocationTree with this MemberSelectTree as its method select
                    if (grandParent != null && grandParent.getLeaf() instanceof MethodInvocationTree methodInvocation) {
                        if (methodInvocation.getMethodSelect().equals(memberSelect)) {
                            current = grandParent;
                            continue;
                        }
                    }
                }
            }

            break;
        }

        return current;
    }

    public static Optional<Type> getFirstTypeArgument(Type type) {
        if (type instanceof ClassType classType && !classType.getTypeArguments().isEmpty()) {
            return Optional.ofNullable(classType.getTypeArguments().get(0));
        }
        return Optional.empty();
    }

    private TreeUtils() {}
}
