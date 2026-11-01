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
import org.rulii.explorer.descriptor.BeanInfo;
import org.rulii.explorer.descriptor.Descriptor;
import org.rulii.explorer.descriptor.Discovery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code packages}, {@code classes} and {@code include-validated=false}: only what the properties
 * name is described, and what the validator validated is left out.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"rulii.explorer.beans.enabled=true", "rulii.explorer.beans.packages=com.acme.beans",
                "rulii.explorer.beans.classes=org.rulii.explorer.boot.beans.Customer", "rulii.explorer.beans.include-validated=false"})
class BeansConfiguredTest {

    @Autowired
    private DescriptorService service;

    @Autowired
    private Validator validator;

    @Test
    void describesOnlyWhatIsNamed() {
        Order order = new Order();
        order.setTotal(5);
        validator.validate(order);

        Descriptor descriptor = service.snapshot().descriptor();
        assertEquals(List.of(Legacy.class.getName(), Customer.class.getName()), descriptor.beans().stream().map(BeanInfo::id).toList(),
                "the configured package, the configured class; not the validated Order");
        assertEquals(Discovery.SCAN, descriptor.beans().get(0).discovery());
        assertEquals(Discovery.CONFIGURED, descriptor.beans().get(1).discovery());
    }
}
