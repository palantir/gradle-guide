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
