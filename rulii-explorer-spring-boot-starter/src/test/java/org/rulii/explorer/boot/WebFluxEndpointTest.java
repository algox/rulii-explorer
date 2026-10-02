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
import org.rulii.explorer.boot.ui.UiPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebFlux instead of MVC (NFR-11): the same endpoint, the same body.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.endpoints.web.exposure.include=rulii", "spring.main.web-application-type=reactive"})
class WebFluxEndpointTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UiPage page;

    @Test
    void servesTheDescriptorReactively() {
        Http.Response response = Http.get(port, "/actuator/rulii");
        assertEquals(200, response.status(), response.body());

        Descriptor served = DescriptorJson.fromJson(response.body());
        assertEquals(3, served.artifacts().size());
        assertTrue(served.artifacts().stream().anyMatch(a -> a.id().equals("orderFlow")));
    }

    @Test
    void servesTheUiReactively() {
        Http.Response html = Http.get(port, "/rulii");
        assertEquals(200, html.status());
        assertTrue(html.header().startsWith("text/html"), html.header());
        assertTrue(html.body().contains("<meta name=\"rulii-descriptor\" content=\"/actuator/rulii\">"), html.body());
        assertEquals(200, Http.get(port, "/rulii/").status());

        Http.Response asset = Http.get(port, "/rulii/" + page.assetSegment() + "/app/main.js", "Cache-Control");
        assertEquals(200, asset.status());
        assertTrue(asset.header().contains("immutable"), asset.header());
        assertEquals(404, Http.get(port, "/rulii/index.html").status());
    }
}
