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

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import tools.jackson.databind.JsonNode;

/**
 * {@code GET /actuator/rulii}: the descriptor. Read-only (NFR-1), reachable only when the
 * application exposes it with {@code management.endpoints.web.exposure.include=rulii} (NFR-2),
 * and protected by whatever protects Actuator.
 *
 * <p>The body is the descriptor JSON with nulls omitted, the same tree {@code DescriptorJson}
 * writes (only the whitespace differs), so a {@code curl} of this endpoint compares cleanly with a
 * build-time descriptor (FR-42). When the build failed, the body is an error payload with status 500.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
@Endpoint(id = RuliiDescriptorEndpoint.ID)
public class RuliiDescriptorEndpoint {

    public static final String ID = "rulii";

    private final DescriptorService service;

    public RuliiDescriptorEndpoint(DescriptorService service) {
        super();
        this.service = service;
    }

    @ReadOperation
    public WebEndpointResponse<JsonNode> descriptor() {
        DescriptorService.Snapshot snapshot = service.snapshot();
        return new WebEndpointResponse<>(snapshot.json(),
                snapshot.failed() ? WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR : WebEndpointResponse.STATUS_OK);
    }
}
