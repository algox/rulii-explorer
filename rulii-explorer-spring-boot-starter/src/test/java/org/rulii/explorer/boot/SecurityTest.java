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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.*;

/**
 * With Spring Security on, the descriptor is protected by whatever protects Actuator: an
 * anonymous request gets 401 (SOLUTION §8.3). The UI shows a designed state for this.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.endpoints.web.exposure.include=rulii", "spring.autoconfigure.exclude="})
class SecurityTest {

    @LocalServerPort
    private int port;

    @Test
    void anonymousRequestIsRejected() {
        Http.Response response = Http.get(port, "/actuator/rulii", "WWW-Authenticate");
        assertEquals(401, response.status());
        assertNotNull(response.header(), "the application's security asks for credentials");
    }
}
