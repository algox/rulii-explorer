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
package org.rulii.explorer.problem;

import java.util.List;

/**
 * The checks the explorer runs by default.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ProblemChecks {

    private ProblemChecks() {
        super();
    }

    /** Unresolved targets, duplicate names, unused rules and missing descriptions. */
    public static List<ProblemCheck> defaults() {
        return List.of(new UnresolvedTargetCheck(), new DuplicateNameCheck(), new UnusedRuleCheck(),
                new MissingDescriptionCheck());
    }
}
