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
 * A rule the library materialized for a bean or an executable (FR-65). It belongs to the bean:
 * it is not an artifact, not in the registry, and not listed with the application's rules. The
 * detail is what a rule artifact carries, so the UI renders it with the same component.
 *
 * @param id         {@code <beanId>/rules/<name>} or {@code <beanId>/<executableKey>/rules/<name>};
 *                   for deep links.
 * @param name       the rule's name, unique within the bean or executable.
 * @param placement  where it sits.
 * @param kind       as for a rule artifact: predefined validator, Java builder, ...
 * @param parameters the rule's declared parameters.
 * @param rule       the rule's conditions and actions; null for a predefined validator.
 * @param validation the validation detail: error code, severity, message, value source, settings.
 * @param hosted     a composed constraint's rules, recursively; empty otherwise.
 *
 * @author Algorithmx Development Team
 * @since 1.1
 */
public record BeanRule(String id, String name, Placement placement, ArtifactKind kind, List<Parameter> parameters,
                       RuleDetails rule, ValidationDetails validation, List<BeanRule> hosted) {

    public BeanRule {
        parameters = Lists.copy(parameters);
        hosted = Lists.copy(hosted);
    }
}
