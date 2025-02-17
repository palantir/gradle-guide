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

import com.palantir.gradle.guide.internal.markdown.MdFile;
import java.util.function.UnaryOperator;

public final class SingleContentChanger implements ContentChanger {
    private static final boolean CI = System.getenv("CI") != null;

    private final MdFile mdFile;
    private final UnaryOperator<String> markdownContentModifier;

    public SingleContentChanger(MdFile mdFile, UnaryOperator<String> markdownContentModifier) {
        this.mdFile = mdFile;
        this.markdownContentModifier = markdownContentModifier;
    }

    @Override
    public void verifyContentOnCiOrChangeContentLocally() {
        String existing = mdFile.readContent();
        String expected = markdownContentModifier.apply(existing);

        if (existing.equals(expected)) {
            return;
        }

        if (CI) {
            throw new ContentNotCorrectException(mdFile.path());
        }

        mdFile.writeContent(expected);
    }

    @Override
    public void changeContent() {
        mdFile.writeContent(markdownContentModifier.apply(mdFile.readContent()));
    }
}
