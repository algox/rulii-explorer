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

import java.util.List;

/**
 * One rule, rule set or rule flow. The section matching {@link #type()} is set ({@code rule},
 * {@code ruleSet} or {@code ruleFlow}); {@code validation} is set for rules that are validation
 * rules.
 *
 * @param id          the registry key (in Spring, the bean name); for inline artifacts a path such as {@code orderValidationRules/members[2]}.
 * @param name        the artifact own name (FR-4), which may differ from the id.
 * @param type        rule, ruleset or ruleflow.
 * @param kind        how it was declared.
 * @param registered  false for an inline member or target that is not in the registry.
 * @param packageId   the package it belongs to; null when unknown (FR-30).
 * @param description the description; null when none.
 * @param category    the business category it belongs to, {@code /} between the levels ({@code Pricing/Discounts}); null when none.
 * @param tags        what it is about, in the order declared, each once; null when none (since 1.1).
 * @param source      where it was declared; null when unknown or hidden (NFR-4).
 * @param className   the rule class of a class-based rule; null otherwise or when hidden.
 * @param parameters  declared parameters, in order.
 * @param rule        rule details; rules only.
 * @param validation  validation details; validation rules only.
 * @param ruleSet     rule set details; rule sets only.
 * @param ruleFlow    rule flow details; rule flows only.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
public record Artifact(String id, String name, ArtifactType type, ArtifactKind kind, boolean registered,
                       String packageId, String description, String category, List<String> tags, Source source, String className,
                       List<Parameter> parameters, RuleDetails rule, ValidationDetails validation,
                       RuleSetDetails ruleSet, RuleFlowDetails ruleFlow) {

    public Artifact {
        parameters = Lists.copy(parameters);
        // Empty tags are left out of the JSON like every other absent value, so null, not [].
        tags = tags == null || tags.isEmpty() ? null : List.copyOf(tags);
    }
}
