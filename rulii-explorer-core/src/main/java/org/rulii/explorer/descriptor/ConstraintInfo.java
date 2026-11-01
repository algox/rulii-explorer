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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One constraint annotation as declared (FR-64), with the plain phrase of FR-69 and the id of
 * the bean rule the library built from it (FR-65).
 *
 * @param annotation              the annotation's class name.
 * @param name                    the annotation's simple name with a lower-case initial
 *                                ({@code notBlank}): the search token.
 * @param attributes              the annotation's attributes other than {@code message},
 *                                {@code groups} and {@code payload}, as declared; values are
 *                                strings, numbers, booleans, class names or lists of those.
 * @param plain                   the plain phrase ("is at least 0.0"); the annotation and its
 *                                attributes when no phrase is known.
 * @param message                 the message template as declared.
 * @param groups                  the groups, simple class names; empty for Default.
 * @param severity                the rulii severity the library gives it, when declared.
 * @param payload                 payload class names.
 * @param reportAsSingleViolation whether a composed constraint reports one violation.
 * @param composing               the composing constraints of a composed constraint.
 * @param ruleId                  the bean rule's id; null when the bean did not materialize.
 *
 * @author Algorithmx Development Team
 * @since 1.1
 */
public record ConstraintInfo(String annotation, String name, Map<String, Object> attributes, String plain,
                             String message, List<String> groups, String severity, List<String> payload,
                             boolean reportAsSingleViolation, List<ConstraintInfo> composing, String ruleId) {

    public ConstraintInfo {
        attributes = attributes == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(attributes));
        groups = Lists.copy(groups);
        payload = Lists.copy(payload);
        composing = Lists.copy(composing);
    }
}
