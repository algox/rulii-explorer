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
 * What a rule does (FR-10).
 *
 * @param preCondition guard; null when none.
 * @param given        the condition.
 * @param then         actions run when the condition holds, in order.
 * @param otherwise    action run when it does not; null when none.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public record RuleDetails(Expression preCondition, Expression given, List<Expression> then, Expression otherwise) {

    public RuleDetails {
        then = Lists.copy(then);
    }
}
