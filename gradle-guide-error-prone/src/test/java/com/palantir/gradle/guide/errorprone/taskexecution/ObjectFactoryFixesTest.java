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

import org.junit.jupiter.api.Test;

public class ObjectFactoryFixesTest extends IllegalMethodCalledDuringTaskExecutionTest {
    @Test
    void should_fix() {
        testFix("CustomTask.java", """
                import org.gradle.api.DefaultTask;
                import org.gradle.api.tasks.TaskAction;
                import org.gradle.api.logging.Logger;

                abstract class CustomTask extends DefaultTask {
                    @TaskAction
                    final void action() {
                        getProject().getObjects().setProperty(String.class);
                    }
                }
            """, """
                import javax.inject.Inject;
                import org.gradle.api.DefaultTask;
                import org.gradle.api.logging.Logger;
                import org.gradle.api.model.ObjectFactory;
                import org.gradle.api.tasks.TaskAction;

                abstract class CustomTask extends DefaultTask {

                    @Inject
                    protected abstract ObjectFactory getObjectFactory();

                    @TaskAction
                    final void action() {
                        getObjectFactory().setProperty(String.class);
                    }
                }
            """);
    }
}
