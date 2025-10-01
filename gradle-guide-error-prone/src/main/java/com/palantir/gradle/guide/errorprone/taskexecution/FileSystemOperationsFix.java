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

package com.palantir.gradle.guide.errorprone.taskexecution;

public class FileSystemOperationsFix extends ServiceBasedGradleFix {

    private final String template;

    private FileSystemOperationsFix(String template) {
        this.template = template;
    }

    public static final FileSystemOperationsFix COPY = new FileSystemOperationsFix("copy(%s)");
    public static final FileSystemOperationsFix DELETE = new FileSystemOperationsFix("delete(%s)");

    @Override
    protected GradleService gradleService() {
        return GradleService.FILE_SYSTEMS_OPERATIONS;
    }

    @Override
    protected String fixedMethod() {
        return template;
    }
}
