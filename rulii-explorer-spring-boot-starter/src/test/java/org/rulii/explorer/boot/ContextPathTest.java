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
import org.rulii.explorer.boot.ui.UiPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A servlet context path and a custom Actuator base path are respected (NFR-11).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.endpoints.web.exposure.include=rulii", "server.servlet.context-path=/app",
                "management.endpoints.web.base-path=/manage"})
class ContextPathTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UiPage page;

    @Test
    void endpointFollowsContextAndBasePath() {
        assertEquals(200, Http.get(port, "/app/manage/rulii").status());
        assertEquals(404, Http.get(port, "/actuator/rulii").status());
    }

    @Test
    void uiPointsAtTheRelocatedEndpoint() {
        Http.Response response = Http.get(port, "/app/rulii");
        assertEquals(200, response.status());
        assertTrue(response.body().contains("<meta name=\"rulii-descriptor\" content=\"/app/manage/rulii\">"), response.body());
        assertTrue(response.body().contains("src=\"/app/rulii/" + page.assetSegment() + "/app/main.js\""), response.body());
        assertEquals(200, Http.get(port, "/app/rulii/" + page.assetSegment() + "/app/main.js").status());
    }
}
