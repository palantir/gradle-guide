/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.guide;

import com.palantir.gradle.suppressibleerrorprone.SuppressibleErrorPronePlugin;
import java.util.Optional;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class GradleGuidePlugin implements Plugin<Project> {
    @Override
    public final void apply(Project project) {
        project.getPluginManager().withPlugin("java-gradle-plugin", _ignored -> {
            applyToJavaProject(project);
        });
    }

    private static void applyToJavaProject(Project project) {
        project.getPluginManager().apply(SuppressibleErrorPronePlugin.class);

        String possibleVersion = Optional.ofNullable(
                        GradleGuidePlugin.class.getPackage().getImplementationVersion())
                .map(version -> ":" + version)
                .orElse("");

        project.getDependencies()
                .add("errorprone", "com.palantir.gradle.guide:gradle-guide-error-prone" + possibleVersion);
    }
}
