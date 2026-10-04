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
 * How an artifact was declared.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
public enum ArtifactKind {
    /** Declared in an XML file with script expressions. */
    XML_SCRIPT("xml-script"),
    /** A rule set or rule flow declared in an XML file. */
    XML("xml"),
    /** A {@code @Rule}-annotated class. */
    RULE_CLASS("rule-class"),
    /** Built in Java with a builder (lambdas or scripts). */
    JAVA_BUILDER("java-builder"),
    /** One of rulii's predefined validators (notNull, email, min, pattern ...). */
    PREDEFINED_VALIDATOR("predefined-validator"),
    /** Could not be determined: describing the artifact failed (NFR-22). */
    UNKNOWN("unknown");

    private final String json;

    ArtifactKind(String json) {
        this.json = json;
    }

    /** The value used in the descriptor. */
    @JsonValue
    public String json() {
        return json;
    }
}
