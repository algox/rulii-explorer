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
import org.rulii.explorer.descriptor.Descriptor;
import org.rulii.explorer.descriptor.DescriptorJson;
import org.rulii.explorer.problem.ProblemCodes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spring MVC, endpoint exposed: the descriptor is served and matches what the service built.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.endpoints.web.exposure.include=rulii", "spring.application.name=order-service"})
class MvcEndpointTest {

    @LocalServerPort
    private int port;

    @Autowired
    private DescriptorService service;

    @Test
    void servesTheDescriptor() {
        Http.Response response = Http.get(port, "/actuator/rulii");

        assertEquals(200, response.status(), response.body());
        assertTrue(response.header().startsWith("application/vnd.spring-boot.actuator") || response.header().startsWith("application/json"), response.header());

        Descriptor served = DescriptorJson.fromJson(response.body());
        assertEquals("1.1", served.descriptorVersion());
        assertEquals("order-service", served.application().name(), "defaults to spring.application.name");
        assertEquals(service.snapshot().descriptor(), served, "the endpoint serves exactly what the service built");

        assertTrue(served.artifacts().stream().anyMatch(a -> a.id().equals("minTotalRule") && a.name().equals("MinTotalRule")),
                "ids are bean names, names are the artifacts' own");
        assertTrue(served.problems().stream().anyMatch(p -> p.code().equals(ProblemCodes.UNRESOLVED_TARGET)));
        assertFalse(response.body().contains(":null"), "nulls are omitted, like DescriptorJson");
    }

    @Test
    void isReadOnly() {
        assertEquals(200, Http.get(port, "/actuator/rulii").status());
        assertEquals(405, post(port, "/actuator/rulii"));
    }

    private static int post(int port, String path) {
        try {
            var client = java.net.http.HttpClient.newHttpClient();
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + path))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}"))
                    .header("Content-Type", "application/json").build();
            return client.send(request, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
