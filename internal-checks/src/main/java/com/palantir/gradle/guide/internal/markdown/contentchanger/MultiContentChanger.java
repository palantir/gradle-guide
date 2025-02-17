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

package com.palantir.gradle.guide.internal.markdown.contentchanger;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MultiContentChanger implements ContentChanger {
    private final List<ContentChanger> contentChangers;

    public MultiContentChanger(List<ContentChanger> contentChangers) {
        this.contentChangers = contentChangers;
    }

    @Override
    public void verifyContentOnCiOrChangeContentLocally() {
        Set<Path> paths = new LinkedHashSet<>();

        for (ContentChanger contentChanger : contentChangers) {
            try {
                contentChanger.verifyContentOnCiOrChangeContentLocally();
            } catch (ContentNotCorrectException e) {
                paths.addAll(e.mdFilePaths());
            }
        }

        if (!paths.isEmpty()) {
            throw new ContentNotCorrectException(paths);
        }
    }

    @Override
    public void changeContent() {
        contentChangers.forEach(ContentChanger::changeContent);
    }
}
