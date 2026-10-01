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

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * What a validation rule reports and, for a predefined validator, how it is configured (FR-11).
 *
 * @param validator      the predefined validator name ({@code notNull}, {@code email}, {@code min} ...); null for a custom validation rule.
 * @param errorCode      the violation error code.
 * @param severity       FATAL, ERROR, WARNING or INFO.
 * @param errorMessage   the message template; null when none.
 * @param defaultMessage the fallback message; null when none.
 * @param valueSource    where the checked value comes from; predefined validators only.
 * @param failOnNull     whether a null value fails the rule; predefined validators only.
 * @param settings       validator-specific settings ({@code min}, {@code pattern}, {@code values} ...), sorted by name.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public record ValidationDetails(String validator, String errorCode, String severity, String errorMessage,
                                String defaultMessage, ValueSource valueSource, Boolean failOnNull,
                                Map<String, Object> settings) {

    public ValidationDetails {
        settings = settings == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(settings));
    }
}
