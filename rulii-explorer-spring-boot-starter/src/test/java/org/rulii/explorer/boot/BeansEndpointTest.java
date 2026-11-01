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
import org.rulii.explorer.boot.beans.Order;
import org.rulii.explorer.descriptor.BeanInfo;
import org.rulii.explorer.descriptor.Descriptor;
import org.rulii.explorer.descriptor.DescriptorJson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spring MVC, endpoint exposed, beans on: the served descriptor carries the beans section and
 * matches what the service built.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.endpoints.web.exposure.include=rulii", "rulii.explorer.beans.enabled=true"})
class BeansEndpointTest {

    @LocalServerPort
    private int port;

    @Autowired
    private DescriptorService service;

    @Test
    void servesTheBeans() {
        Http.Response response = Http.get(port, "/actuator/rulii");
        assertEquals(200, response.status(), response.body());

        Descriptor served = DescriptorJson.fromJson(response.body());
        assertEquals("1.1", served.descriptorVersion());
        assertNotNull(served.beans());
        assertTrue(served.beans().stream().map(BeanInfo::id).anyMatch(Order.class.getName()::equals), served.beans().toString());
        assertEquals(service.snapshot().descriptor(), served, "the endpoint serves exactly what the service built");
        assertFalse(response.body().contains(":null"), "nulls are omitted, like DescriptorJson");
    }
}
