/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.guide;

import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.palantir.gradle.guide.EnableChecksThatPass.EnableChecksThatPassTask;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.ltgt.gradle.errorprone.ErrorProneOptions;
import net.ltgt.gradle.errorprone.ErrorPronePlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;

public class GradleGuidePlugin implements Plugin<Project> {
    private static final Pattern COMPILE_ERROR_PATTERN = Pattern.compile("error: \\[(\\w+)\\]");

    @Override
    public final void apply(Project project) {
        project.getPluginManager().withPlugin("java-gradle-plugin", _ignored -> {
            applyToJavaProject(project);
        });
    }

    private static void applyToJavaProject(Project project) {
        project.getPluginManager().apply(ErrorPronePlugin.class);

        String possibleVersion = Optional.ofNullable(
                        GradleGuidePlugin.class.getPackage().getImplementationVersion())
                .map(version -> ":" + version)
                .orElse("");

        project.getDependencies()
                .add(
                        ErrorPronePlugin.CONFIGURATION_NAME,
                        "com.palantir.gradle.guide:gradle-guide-error-prone" + possibleVersion);

        project.getTasks().withType(JavaCompile.class).configureEach(javaCompile -> {
            ((ExtensionAware) javaCompile.getOptions())
                    .getExtensions()
                    .configure(ErrorProneOptions.class, errorProneOptions -> {
                        disabledErrorprones(project).forEach(errorProneOptions::disable);
                    });
        });

        TaskProvider<EnableChecksThatPassTask> enableChecksThatPass = project.getTasks()
                .register("enableGradleGuideChecksThatPass", EnableChecksThatPassTask.class, enableChecks -> {
                    enableChecks.getConfigFile().set(project.file(".palantir/gradle-guide.yml"));
                    enableChecks.getAllChecks().set(project.provider(GradleGuidePlugin::allErrorprones));
                    enableChecks.dependsOn(project.getTasks().withType(JavaCompile.class));
                });

        project.getGradle().getTaskGraph().whenReady(taskGraph -> {
            EnableChecksThatPassTask enableChecksThatPassTask = enableChecksThatPass.get();

            if (!taskGraph.hasTask(enableChecksThatPassTask)) {
                return;
            }

            project.getTasks().withType(JavaCompile.class).configureEach(javaCompile -> {
                javaCompile.getOutputs().upToDateWhen(_ignored -> false);
                javaCompile.getLogging().addStandardErrorListener(output -> {
                    Matcher matcher = COMPILE_ERROR_PATTERN.matcher(output);

                    while (matcher.find()) {
                        String error = matcher.group(1);
                        enableChecksThatPassTask.getErroringChecks().add(error);
                    }
                });
            });
        });
    }

    private static Set<String> disabledErrorprones(Project project) {
        return Sets.difference(allErrorprones(), enabledErrorprones(project));
    }

    private static Set<String> allErrorprones() {
        try {
            return Set.copyOf(
                    Resources.readLines(Resources.getResource("gradle-guide/errorprones"), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Set<String> enabledErrorprones(Project project) {
        Path configFile = project.getRootDir().toPath().resolve(".palantir/gradle-guide.yml");

        if (Files.notExists(configFile)) {
            return Set.of();
        }

        return ConfigFile.fromYaml(configFile).enabled();
    }
}
