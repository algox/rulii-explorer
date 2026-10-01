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
package com.acme.order;

import org.junit.jupiter.api.Test;
import org.rulii.explorer.Explorer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The demo serves the explorer UI next to its descriptor, with nothing configured beyond the
 * endpoint exposure in application.yaml.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UiServingTest {

    @LocalServerPort
    private int port;

    @Test
    void servesTheExplorer() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> page = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/rulii-explorer")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("<meta name=\"rulii-descriptor\" content=\"/actuator/rulii\">"), page.body());
        assertTrue(page.body().contains("<rx-app></rx-app>"), page.body());

        HttpResponse<String> asset = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/rulii-explorer/" + Explorer.version() + "/app/main.js")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, asset.statusCode());
        assertTrue(asset.body().contains("loadDescriptor"), "the app's entry point");

        HttpResponse<String> descriptor = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/rulii")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, descriptor.statusCode());
    }
}
