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
 * How a run step names what it runs.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
public enum TargetKind {
    /** A runnable given at build time: a Java instance or an XML {@code bean-ref}. */
    INSTANCE("instance"),
    /** A registry lookup by name when the step runs. */
    BY_NAME("by-name"),
    /** A registry lookup by rule class when the step runs. */
    BY_CLASS("by-class");

    private final String json;

    TargetKind(String json) {
        this.json = json;
    }

    /** The value used in the descriptor. */
    @JsonValue
    public String json() {
        return json;
    }
}
