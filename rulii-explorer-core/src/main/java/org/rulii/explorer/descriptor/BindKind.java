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
 * What a {@code @Bind} binds: a literal converted to the declared type, or an expression
 * evaluated when first read (FR-68).
 *
 * @author Algorithmx Development Team
 * @since 1.1
 */
public enum BindKind {

    LITERAL("literal"),
    EXPRESSION("expression");

    private final String json;

    BindKind(String json) {
        this.json = json;
    }

    @JsonValue
    public String json() {
        return json;
    }
}
