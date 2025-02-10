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

import com.google.common.collect.Sets;
import com.palantir.gradle.autoparallelizable.AutoParallelizable;
import java.util.Set;
import java.util.TreeSet;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;

@AutoParallelizable
final class EnableChecksThatPass {
    abstract static class EnableChecksThatPassTask extends EnableChecksThatPassTaskImpl {
        @SuppressWarnings("checkstyle:RedundantModifier")
        public EnableChecksThatPassTask() {}
    }

    interface Params {
        @OutputFile
        RegularFileProperty getConfigFile();

        @Input
        SetProperty<String> getErroringChecks();

        @Input
        SetProperty<String> getAllChecks();
    }

    static void action(Params params) {
        ConfigFile existing =
                ConfigFile.fromYaml(params.getConfigFile().get().getAsFile().toPath());

        Set<String> toEnable = Sets.difference(
                params.getAllChecks().get(), params.getErroringChecks().get());

        ConfigFile updated = ConfigFile.builder()
                .from(existing)
                .enabled(new TreeSet<>(toEnable))
                .build();

        updated.writeTo(params.getConfigFile().get().getAsFile().toPath());
    }

    private EnableChecksThatPass() {}
}
