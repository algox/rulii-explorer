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

import com.acme.beans.Legacy;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.rulii.explorer.boot.beans.Customer;
import org.rulii.explorer.boot.beans.Order;
import org.rulii.explorer.builder.BeanSource;
import org.rulii.explorer.descriptor.BeanInfo;
import org.rulii.explorer.descriptor.Descriptor;
import org.rulii.explorer.descriptor.DescriptorJson;
import org.rulii.explorer.descriptor.Discovery;
import org.rulii.explorer.descriptor.ReferenceType;
import org.rulii.validation.beans.BeanValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code rulii.explorer.beans.enabled=true} with the defaults: the auto-configuration package is
 * scanned, what the validator validated is included, and the build-time descriptor carries the
 * beans too (descriptor 1.1).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"rulii.explorer.beans.enabled=true", "rulii.explorer.include-sources=false"})
class BeansEnabledTest {

    @Autowired
    private DescriptorService service;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Validator validator;

    @Test
    void scansTheAutoConfigurationPackageAndIncludesWhatWasValidated() {
        assertNotNull(context.getBean(BeanSource.class));
        assertSame(context.getBean(BeanValidator.class), context.getBean(BeanSource.class).validator(), "rulii-spring's validator");

        Legacy legacy = new Legacy();
        legacy.setCode("ok");
        assertTrue(validator.validate(legacy).isEmpty());

        Descriptor descriptor = service.snapshot().descriptor();
        assertNotNull(descriptor.beans());
        assertEquals(List.of(Legacy.class.getName(), Customer.class.getName(), Order.class.getName()),
                descriptor.beans().stream().map(BeanInfo::id).toList(), "sorted by id; the test application's package scanned");
        assertEquals(Discovery.VALIDATED, descriptor.beans().get(0).discovery(), "validated before the build, outside the scanned package");
        assertEquals(Discovery.SCAN, descriptor.beans().get(1).discovery());
        assertEquals(Discovery.SCAN, descriptor.beans().get(2).discovery());

        BeanInfo order = descriptor.beans().get(2);
        assertTrue(order.materialized());
        assertEquals("minTotalRule", order.validations().get(0).targets().get(0).artifactId(), "@Validate resolves against the registry's bean names");
        assertTrue(descriptor.references().stream().anyMatch(r -> r.from().equals(Order.class.getName())
                && r.to().equals(Customer.class.getName()) && r.type() == ReferenceType.CASCADES));
        assertTrue(descriptor.references().stream().anyMatch(r -> r.from().equals(Order.class.getName())
                && r.to().equals("minTotalRule") && r.type() == ReferenceType.RUNS));
        assertNull(order.source(), "include-sources=false hides bean sources too");
    }

    @Test
    void theBuildTimeDescriptorCarriesTheBeans() throws IOException {
        Path file = Path.of("target", "rulii-descriptor-beans-test.json");
        Files.deleteIfExists(file);
        RuliiDescriptors.write(context, file);

        String json = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(json.contains("\n  \"beans\": [\n"), "the beans section is written");
        assertEquals(service.snapshot().descriptor(), DescriptorJson.fromJson(json));
    }
}
