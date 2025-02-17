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

package com.palantir.gradle.guide.internal.text;

import java.util.regex.Pattern;

public record SectionTag(String startTag, String endTag) {
    public SectionTag(String tagName) {
        this("<!-- " + tagName + ":START -->", "<!-- " + tagName + ":END -->");
    }

    public String removeExistingTaggedSectionsAndPreceedingWhitespace(String text) {
        Pattern pattern = Pattern.compile(
                "(\\s|\\n)*" + Pattern.quote(startTag) + "(.|\\n)*?" + Pattern.quote(endTag) + "(\\s|\\n)*");

        return pattern.matcher(text).replaceAll("");
    }

    public String replaceTaggedSection(String text, String replacement) {
        int start = text.indexOf(startTag) + startTag.length();

        if (start == -1) {
            throw new RuntimeException("Can't find start tag '%s' in:\n\n%s".formatted(startTag, text));
        }

        int end = text.indexOf(endTag);

        if (end == -1) {
            throw new RuntimeException("Can't find end tag '%s' in:\n\n%s".formatted(endTag, text));
        }

        return text.substring(0, start) + "\n" + replacement + "\n" + text.substring(end);
    }
}
