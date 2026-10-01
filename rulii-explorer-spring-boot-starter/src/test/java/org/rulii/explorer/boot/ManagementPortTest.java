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
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Actuator on a separate management port: the descriptor moves with it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.endpoints.web.exposure.include=rulii", "management.server.port=0"})
class ManagementPortTest {

    @LocalServerPort
    private int serverPort;

    @Autowired
    private UiPage page;

    @LocalManagementPort
    private int managementPort;

    @Test
    void endpointIsOnTheManagementPortOnly() {
        assertNotEquals(serverPort, managementPort);
        assertEquals(200, Http.get(managementPort, "/actuator/rulii").status());
        assertEquals(404, Http.get(serverPort, "/actuator/rulii").status());
    }

    @Test
    void uiFollowsTheDescriptorToTheManagementPort() {
        Http.Response html = Http.get(managementPort, "/rulii-explorer");
        assertEquals(200, html.status(), html.body());
        assertTrue(html.body().contains("<meta name=\"rulii-descriptor\" content=\"/actuator/rulii\">"), html.body());
        assertEquals(200, Http.get(managementPort, "/rulii-explorer/" + page.assetSegment() + "/app/main.js").status());
        assertEquals(404, Http.get(serverPort, "/rulii-explorer").status(), "not on the application port");
    }
}
