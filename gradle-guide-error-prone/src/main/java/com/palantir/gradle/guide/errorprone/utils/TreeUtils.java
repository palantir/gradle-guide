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
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.tree.JCTree;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public final class TreeUtils {
    public static int startPosition(Tree tree) {
        return ((JCTree) tree).getStartPosition();
    }

    public static Stream<TreePath> pathToRoot(TreePath start) {
        return Stream.iterate(start, path -> path.getParentPath() != null, TreePath::getParentPath);
    }

    public static Optional<String> expressionToIdentifier(VisitorState state, ExpressionTree expressionTree) {
        String originalSource = state.getSourceForNode(expressionTree);

        if (originalSource == null || originalSource.isEmpty()) {
            return Optional.empty();
        }

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

        String identifier = stringBuilder.toString();

        if (identifier.length() < 3) {
            return Optional.empty();
        }

        return Optional.of(identifier);
    }

    private TreeUtils() {}
}
