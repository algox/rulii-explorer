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
 * One {@code @Validate} as declared (FR-64): registry rules, rule sets or flows run against the
 * declaring element.
 *
 * @param rules   the names as declared, in order.
 * @param groups  the groups, simple class names; empty for Default.
 * @param applyTo the constraint target, as declared ({@code IMPLICIT} unless set).
 * @param targets one per name, with what it resolves to.
 *
 * @author Algorithmx Development Team
 * @since 1.1
 */
public record ValidationInfo(List<String> rules, List<String> groups, String applyTo, List<ValidationTarget> targets) {

    public ValidationInfo {
        rules = Lists.copy(rules);
        groups = Lists.copy(groups);
        targets = Lists.copy(targets);
    }
}
