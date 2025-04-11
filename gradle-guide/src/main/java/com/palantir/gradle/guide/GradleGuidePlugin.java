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
        project.getPluginManager().withPlugin("java-library", _ignored -> {
            applyToJavaProject(project);
        });
    }

    private static void applyToJavaProject(Project project) {
        project.getConfigurations().getByName("api").getDependencies().whenObjectAdded(dependency -> {
            if (!dependency.equals(project.getDependencies().gradleApi())) {
                return;
            }

            project.getPluginManager().apply(SuppressibleErrorPronePlugin.class);

            project.getConfigurations().named("errorprone", errorProneConfig -> {
                String possibleVersion = Optional.ofNullable(
                                GradleGuidePlugin.class.getPackage().getImplementationVersion())
                        .map(version -> ":" + version)
                        .orElse("");

                errorProneConfig
                        .getDependencies()
                        .add(project.getDependencies()
                                .create("com.palantir.gradle.guide:gradle-guide-error-prone" + possibleVersion));
            });

            SuppressibleErrorProneExtension suppressibleErrorProneExtension =
                    project.getExtensions().getByType(SuppressibleErrorProneExtension.class);

            suppressibleErrorProneExtension.getPatchChecks().addAll(PATCH_CHECKS);

            if (project.hasProperty("gradleGuideBestEffortMode")) {
                suppressibleErrorProneExtension.configureEachErrorProneOptions(errorProneOptions -> {
                    errorProneOptions.option("GradleGuide:BestEffortMode", true);
                });
            }
        });

        //        project.getPluginManager().apply(SuppressibleErrorPronePlugin.class);
        //
        //        project.getConfigurations().named("errorprone", errorProneConfig -> {
        //            String possibleVersion = Optional.ofNullable(
        //                            GradleGuidePlugin.class.getPackage().getImplementationVersion())
        //                    .map(version -> ":" + version)
        //                    .orElse("");
        //
        //            errorProneConfig
        //                    .getDependencies()
        //                    .addAllLater(project.getConfigurations()
        //                            .named(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME)
        //                            .map(compileClasspath -> {
        //                                boolean anyCompilationConfigurationHasGradleApiDep = compileClasspath
        //                                        .getAllDependencies()
        //                                        .contains(project.getDependencies().gradleApi());
        //
        //                                if (anyCompilationConfigurationHasGradleApiDep) {
        //                                    return Set.of(project.getDependencies()
        //                                            .create("com.palantir.gradle.guide:gradle-guide-error-prone"
        //                                                    + possibleVersion));
        //                                } else {
        //                                    return Set.of();
        //                                }
        //                            }));
        //        });
        //
        //        SuppressibleErrorProneExtension suppressibleErrorProneExtension =
        //                project.getExtensions().getByType(SuppressibleErrorProneExtension.class);
        //
        //        suppressibleErrorProneExtension.getPatchChecks().addAll(PATCH_CHECKS);
        //
        //        if (project.hasProperty("gradleGuideBestEffortMode")) {
        //            suppressibleErrorProneExtension.configureEachErrorProneOptions(errorProneOptions -> {
        //                errorProneOptions.option("GradleGuide:BestEffortMode", true);
        //            });
        //        }
    }
}
