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

package com.palantir.gradle.guide.errorprone.types;

import com.google.errorprone.VisitorState;
import com.google.errorprone.suppliers.Supplier;
import com.sun.tools.javac.code.Type;

/**
 * Represents a gradle service to be injected as part of an autofix.
 */
@SuppressWarnings("ImmutableEnumChecker")
public enum GradleService {
    BUILD_LAYOUT("org.gradle.api.file.BuildLayout", "getBuildLayout"),
    PROJECT_LAYOUT("org.gradle.api.file.ProjectLayout", "getProjectLayout"),
    FILE_SYSTEMS_OPERATIONS("org.gradle.api.file.FileSystemOperations", "getFileSystemOperations"),
    OBJECT_FACTORY("org.gradle.api.model.ObjectFactory", "getObjectFactory"),
    ARCHIVE_OPERATIONS("org.gradle.api.file.ArchiveOperations", "getArchiveOperations"),
    PROVIDER_FACTORY("org.gradle.api.provider.ProviderFactory", "getProviderFactory"),
    EXEC_OPERATIONS("org.gradle.process.ExecOperations", "getExecOperations");

    private final Supplier<Type> type;
    private final String fullyQualifiedName;
    private final String defaultGetterName;

    /**
     * Create a gradle service.
     * @param fullyQualifiedName The fully qualified class name of the Gradle service, e.g.
     *      {@code org.gradle.api.file.ProjectLayout}
     * @param defaultGetterName The name of the getter which will be injected if the service is not available, e.g.
     *      {@code getProjectLayout}
     */
    GradleService(String fullyQualifiedName, String defaultGetterName) {
        // Ideally, we check whether `fullyQualifiedName` corresponds to an actual Gradle class here,
        // But that'd require adding gradleApi() to errorprone's runtime classpath, which is impossible
        this.type = VisitorState.memoize(state -> state.getTypeFromString(fullyQualifiedName));
        this.fullyQualifiedName = fullyQualifiedName;
        this.defaultGetterName = defaultGetterName;
    }

    public Type getType(VisitorState state) {
        return type.get(state);
    }

    public String fullyQualifiedName() {
        return fullyQualifiedName;
    }

    public String className() {
        return fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf('.') + 1);
    }

    public String defaultGetterName() {
        return defaultGetterName;
    }
}
