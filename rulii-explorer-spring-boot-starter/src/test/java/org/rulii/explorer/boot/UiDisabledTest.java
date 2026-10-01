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
 * {@code rulii.explorer.ui.enabled=false}: the JSON endpoint stays, the UI is gone, and a custom
 * {@code rulii.explorer.ui.path} is honoured when it is on.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.endpoints.web.exposure.include=rulii", "rulii.explorer.ui.enabled=false"})
class UiDisabledTest {

    @LocalServerPort
    private int port;

    @Test
    void uiIsNotServed() {
        assertEquals(200, Http.get(port, "/actuator/rulii").status());
        assertEquals(404, Http.get(port, "/rulii-explorer").status());
        assertEquals(404, Http.get(port, "/rulii-explorer/").status());
        assertEquals(404, Http.get(port, "/rulii-explorer/1.0.0-SNAPSHOT/app/main.js").status());
    }

    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"management.endpoints.web.exposure.include=rulii", "rulii.explorer.ui.path=/rules/"})
    static class CustomPathTest {

        @LocalServerPort
        private int port;

        @Autowired
        private UiPage page;

        @Test
        void uiMovesToTheConfiguredPath() {
            Http.Response response = Http.get(port, "/rules");
            assertEquals(200, response.status());
            assertTrue(response.body().contains("href=\"/rules/" + page.assetSegment() + "/app/design/tokens.css\""), response.body());
            assertEquals(200, Http.get(port, "/rules/" + page.assetSegment() + "/app/main.js").status());
            assertEquals(404, Http.get(port, "/rulii-explorer").status());
        }
    }
}
