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

package com.palantir.gradle.guide.internal;

import com.palantir.gradle.guide.internal.markdown.Guide;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

final class GuideTest {
    private final Guide guide = Guide.fromRootDirectory(Paths.get(".."));

    @Test
    void table_of_contents_is_up_to_date() {
        guide.readme().tableOfContents().verifyContentOnCiOrChangeContentLocally();
    }

    @Test
    void previous_next_links_are_up_to_date() {
        guide.previousNextLinks().verifyContentOnCiOrChangeContentLocally();
    }
}
