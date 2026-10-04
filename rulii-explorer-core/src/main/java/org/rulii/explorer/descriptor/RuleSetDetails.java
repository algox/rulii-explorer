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
 * What a rule set does (FR-12). Hooks rulii adds itself (the default result extractor and
 * error handler, and the violation check of a validating rule set) are not shown.
 *
 * @param validating      whether the rule set throws on severe violations.
 * @param preCondition    guard; null when none.
 * @param initializer     action run once first; null when none.
 * @param stopCondition   checked between rules; null when none.
 * @param finalizer       action run once last; null when none.
 * @param resultExtractor function producing the result; null when rulii defaults apply.
 * @param errorHandler    function handling errors; null when rulii defaults apply.
 * @param members         the member rule ids, in order.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
public record RuleSetDetails(boolean validating, Expression preCondition, Expression initializer,
                             Expression stopCondition, Expression finalizer, Expression resultExtractor,
                             Expression errorHandler, List<String> members) {

    public RuleSetDetails {
        members = Lists.copy(members);
    }
}
