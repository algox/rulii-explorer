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

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.rulii.explorer.builder.BeanSource;
import org.rulii.validation.beans.BeanValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The library is present but the application validates with Hibernate Validator
 * ({@code rulii.validation.enabled=false}): beans are asked for, but there is no rulii validator
 * to read them through, so there is no bean source and no section, and the context starts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"rulii.explorer.beans.enabled=true", "rulii.validation.enabled=false"})
class BeansOptOutTest {

    @Autowired
    private DescriptorService service;

    @Autowired
    private ApplicationContext context;

    @Test
    void noRuliiValidatorNoBeansSection() {
        assertNotNull(context.getBean(Validator.class), "the application still has a Validator, Hibernate's");
        assertEquals(0, context.getBeanNamesForType(BeanValidator.class).length, "rulii-spring did not define its validator");
        assertEquals(0, context.getBeanNamesForType(BeanSource.class).length);
        assertFalse(service.snapshot().failed());
        assertNull(service.snapshot().descriptor().beans());
    }
}
