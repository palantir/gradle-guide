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

import com.palantir.gradle.suppressibleerrorprone.SuppressibleErrorProneExtension;
import com.palantir.gradle.suppressibleerrorprone.SuppressibleErrorPronePlugin;
import java.util.Optional;
import java.util.Set;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class GradleGuidePlugin implements Plugin<Project> {
    private static final Set<String> PATCH_CHECKS = Set.of("ConfigurationAvoidanceRegistration", "ProviderGet");

    @Override
    public final void apply(Project rootProject) {
        if (!rootProject.equals(rootProject.getRootProject())) {
            throw new IllegalStateException(
                    GradleGuidePlugin.class.getSimpleName() + " must be applied to the root project");
        }

        rootProject.allprojects(GradleGuidePlugin::applyToProject);
    }

    private static void applyToProject(Project project) {
        project.getPluginManager().withPlugin("java", _ignored -> {
            applyToJavaProject(project);
        });
    }

    private static void applyToJavaProject(Project project) {
        // We simply apply the gradle-guide errorprones to every project in the repo, even if they do not
        // have a gradleApi() dependency. It's very hard to conditionally apply the suppressible
        // errorprone plugin (the best you can do is either put a whenObjectAdded on the dependencies of
        // each configuration that contributes to compileClasspath, which is bad as it realises all the deps,
        // or always apply the suppressible error prone plugin and conditionally lazily add our errorprone
        // dep based on state the compileClasspath, which may not line up correctly timing wise).
        // So we just apply it unconditionally - it should be fine to have errorprones that work on non-gradle types
        // running provided we make sure they are not too expensive.
        project.getPluginManager().apply(SuppressibleErrorPronePlugin.class);

        String possibleVersion = Optional.ofNullable(
                        GradleGuidePlugin.class.getPackage().getImplementationVersion())
                .map(version -> ":" + version)
                .orElse("");

        project.getDependencies()
                .add("errorprone", "com.palantir.gradle.guide:gradle-guide-error-prone" + possibleVersion);

        SuppressibleErrorProneExtension suppressibleErrorProneExtension =
                project.getExtensions().getByType(SuppressibleErrorProneExtension.class);

        suppressibleErrorProneExtension.getPatchChecks().addAll(PATCH_CHECKS);

        if (project.hasProperty("gradleGuideBestEffortMode")) {
            suppressibleErrorProneExtension.configureEachErrorProneOptions(errorProneOptions -> {
                errorProneOptions.option("GradleGuide:BestEffortMode", true);
            });
        }
    }
}
