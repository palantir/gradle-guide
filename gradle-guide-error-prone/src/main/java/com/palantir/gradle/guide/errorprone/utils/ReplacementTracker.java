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

import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFix.Builder;
import com.sun.source.tree.Tree;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Tracks whether a tree has already been replaced to avoid error-prone throwing an exception when the same tree is
 * replaced multiple times.
 */
public final class ReplacementTracker {
    private static final Set<Tree> ALREADY_REPLACED = Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * Returns true if the tree has already been replaced (so no replacement should be added). If no replacement has
     * been added, the given tree is marked as the replacement.
     */
    private static boolean alreadyReplaced(Tree tree) {
        if (ALREADY_REPLACED.contains(tree)) {
            return false;
        }

        ALREADY_REPLACED.add(tree);
        return true;
    }

    public static final class SuggestedFixBuilder extends SuggestedFix.Builder {
        @Override
        public Builder replace(Tree node, String replaceWith) {
            if (alreadyReplaced(node)) {
                return this;
            } else {
                return super.replace(node, replaceWith);
            }
        }

        @Override
        public Builder replace(Tree node, String replaceWith, int startPosAdjustment, int endPosAdjustment) {
            if (alreadyReplaced(node)) {
                return this;
            } else {
                return super.replace(node, replaceWith, startPosAdjustment, endPosAdjustment);
            }
        }
    }

    private ReplacementTracker() {}
}
