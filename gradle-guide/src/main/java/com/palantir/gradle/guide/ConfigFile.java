/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.guide;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature;
import java.io.IOException;
import java.nio.file.Path;
import java.util.SortedSet;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutableConfigFile.class)
abstract class ConfigFile {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory()
            .disable(Feature.WRITE_DOC_START_MARKER)
            .disable(Feature.SPLIT_LINES)
            .enable(Feature.MINIMIZE_QUOTES)
            .enable(Feature.INDENT_ARRAYS_WITH_INDICATOR));

    public abstract SortedSet<String> enabled();

    public static ConfigFile fromYaml(Path path) {
        try {
            return OBJECT_MAPPER.readValue(path.toFile(), ConfigFile.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void writeTo(Path path) {
        try {
            OBJECT_MAPPER.writeValue(path.toFile(), this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static final class Builder extends ImmutableConfigFile.Builder {}

    public static Builder builder() {
        return new Builder();
    }
}
