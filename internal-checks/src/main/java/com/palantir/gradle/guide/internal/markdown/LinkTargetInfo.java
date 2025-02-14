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

package com.palantir.gradle.guide.internal.markdown;

import java.nio.file.Path;
import java.util.Optional;

public record LinkTargetInfo(String label, Path targetFile, Optional<Anchor> anchor) {
    public String markdownLinkFrom(Path from) {
        String relativePath = from.getParent().relativize(targetFile).toString();
        return String.format(
                "[%s](%s%s)", label, relativePath, anchor.map(anc -> "#" + anc).orElse(""));
    }
}
