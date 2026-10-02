/*
 * This software is licensed under the Apache 2 license, quoted below.
 *
 * Copyright (c) 1999-2026, Algorithmx Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rulii.explorer.boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code rulii.explorer.enabled} not set: the explorer stays off. It is opt-in so that a production
 * deployment never shows its rules unless someone turned it on. The context runner reads no
 * {@code application.properties}, unlike the {@code @SpringBootTest}s around it.
 */
class NotEnabledTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ExplorerTestApplication.class);

    @Test
    void offUnlessEnabled() {
        runner.run(context -> assertEquals(0, context.getBeanNamesForType(DescriptorService.class).length,
                "nothing is registered without rulii.explorer.enabled=true"));
        runner.withPropertyValues("rulii.explorer.enabled=true")
                .run(context -> assertEquals(1, context.getBeanNamesForType(DescriptorService.class).length));
    }
}
