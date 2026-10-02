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
 * Spring MVC: the UI page carries the application's addresses, the assets live under the
 * versioned path with long caching, and nothing is served outside it (SOLUTION §8.1).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.endpoints.web.exposure.include=rulii"})
class UiServingTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UiPage page;

    @Test
    void servesThePageWithTheApplicationsAddresses() {
        Http.Response response = Http.get(port, "/rulii");
        assertEquals(200, response.status());
        assertTrue(response.header().startsWith("text/html"), response.header());
        assertTrue(response.body().contains("<meta name=\"rulii-descriptor\" content=\"/actuator/rulii\">"), response.body());
        assertTrue(response.body().contains("href=\"/rulii/" + page.assetSegment() + "/app/design/tokens.css\""), "assets are rewritten to the versioned path");
        assertTrue(response.body().contains("\"lit\": \"/rulii/" + page.assetSegment() + "/vendor/lit/lit-core.min.js\""), "the import map too");
        assertFalse(response.body().contains("\"./"), "no relative references remain");

        assertEquals(200, Http.get(port, "/rulii/").status(), "with a trailing slash");
        assertTrue(Http.get(port, "/rulii", "Cache-Control").header().contains("no-cache"), "the page is revalidated");
    }

    @Test
    void servesVersionedAssetsWithLongCaching() {
        String v = page.assetSegment();
        Http.Response main = Http.get(port, "/rulii/" + v + "/app/main.js", "Cache-Control");
        assertEquals(200, main.status());
        assertTrue(main.header().contains("immutable"), main.header());
        assertTrue(main.header().contains("max-age=31536000"), main.header());
        assertEquals(200, Http.get(port, "/rulii/" + v + "/vendor/lit/lit-core.min.js").status());
        assertEquals(200, Http.get(port, "/rulii/" + v + "/vendor/fonts/Newsreader-latin.woff2").status());
        assertEquals(200, Http.get(port, "/rulii/" + v + "/app/design/rulii-mark.svg").status());
        assertEquals(404, Http.get(port, "/rulii/" + v + "/app/nope.js").status());
    }

    @Test
    void rawFilesAreNotServedOutsideTheVersionedPath() {
        assertEquals(404, Http.get(port, "/rulii/index.html").status());
        assertEquals(404, Http.get(port, "/rulii/app/main.js").status());
        assertEquals(404, Http.get(port, "/rulii/vendor/lit/lit-core.min.js").status());
    }
}
