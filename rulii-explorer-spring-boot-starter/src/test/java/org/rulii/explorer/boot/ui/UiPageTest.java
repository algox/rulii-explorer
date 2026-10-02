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
package org.rulii.explorer.boot.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The page rewriting on its own: paths, versions, context paths.
 */
class UiPageTest {

    @Test
    void rewritesAssetsAndTheDescriptorMeta() {
        UiPage page = new UiPage("/rulii", "1.2.3");
        String html = page.render("/app", "/app/manage/rulii");
        assertTrue(html.contains("<meta name=\"rulii-descriptor\" content=\"/app/manage/rulii\">"), html);
        assertTrue(html.contains("href=\"/app/rulii/1.2.3/app/design/tokens.css\""), html);
        assertTrue(html.contains("src=\"/app/rulii/1.2.3/app/main.js\""), html);
        assertTrue(html.contains("\"lit\": \"/app/rulii/1.2.3/vendor/lit/lit-core.min.js\""), html);
        assertFalse(html.contains("\"./"), "every relative reference is rewritten");
        assertEquals("/rulii/1.2.3/**", page.assetPattern());
        assertEquals("/rulii/1.2.3", page.assetsBase(""));
        assertEquals("/rulii/1.2.3", page.assetsBase(null));
        assertEquals("/app/rulii/1.2.3", page.assetsBase("/app/"));
    }

    @Test
    void normalisesPathsAndVersions() {
        assertEquals("/rulii", UiPage.normalisePath(null));
        assertEquals("/rulii", UiPage.normalisePath(" "));
        assertEquals("/rulii", UiPage.normalisePath("/"));
        assertEquals("/rules", UiPage.normalisePath("rules/"));
        assertEquals("/a/b", UiPage.normalisePath("/a/b//"));
        assertEquals("dev", new UiPage("/x", null).version(), "unknown version");
        assertEquals("dev", new UiPage("/x", "1.0/../etc").version(), "unsafe version");
        UiPage snapshot = new UiPage("/x", "1.0.0-SNAPSHOT");
        assertEquals("1.0.0-SNAPSHOT", snapshot.version());
        assertTrue(snapshot.assetSegment().startsWith("1.0.0-SNAPSHOT-") && snapshot.assetSegment().length() > "1.0.0-SNAPSHOT-".length(), "snapshots carry a build fingerprint: " + snapshot.assetSegment());
        assertEquals("/x/" + snapshot.assetSegment() + "/**", snapshot.assetPattern());
        assertTrue(snapshot.render("", "/actuator/rulii").contains("/x/" + snapshot.assetSegment() + "/app/main.js"));
        assertEquals("1.2.3", new UiPage("/x", "1.2.3").assetSegment(), "releases use the plain version");
    }
}
