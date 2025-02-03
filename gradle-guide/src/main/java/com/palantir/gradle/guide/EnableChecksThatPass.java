/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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
