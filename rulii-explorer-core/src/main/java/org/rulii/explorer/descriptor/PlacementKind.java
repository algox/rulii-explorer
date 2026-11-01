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
 * Where a bean rule sits: on a property, a parameter, the return value, across the parameters,
 * or on the bean itself (FR-65).
 *
 * @author Algorithmx Development Team
 * @since 1.1
 */
public enum PlacementKind {

    PROPERTY("property"),
    PARAMETER("parameter"),
    RETURN_VALUE("return-value"),
    CROSS_PARAMETER("cross-parameter"),
    BEAN("bean");

    private final String json;

    PlacementKind(String json) {
        this.json = json;
    }

    @JsonValue
    public String json() {
        return json;
    }
}
