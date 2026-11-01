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
package org.rulii.explorer.descriptor;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How a bean was found (FR-60, FR-62): by the package scan, by configuration, because the
 * validator had already read it, or because another bean cascades into it.
 *
 * @author Algorithmx Development Team
 * @since 1.1
 */
public enum Discovery {

    SCAN("scan"),
    CONFIGURED("configured"),
    VALIDATED("validated"),
    CASCADE("cascade");

    private final String json;

    Discovery(String json) {
        this.json = json;
    }

    @JsonValue
    public String json() {
        return json;
    }
}
