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

import org.rulii.explorer.descriptor.Artifact;
import org.rulii.explorer.descriptor.ArtifactType;
import org.rulii.explorer.descriptor.Problem;
import org.rulii.explorer.descriptor.ProblemSeverity;
import org.rulii.explorer.descriptor.Reference;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A registered rule that no rule set or flow references (info). It may be run directly by
 * application code, so this is informational.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
public final class UnusedRuleCheck implements ProblemCheck {

    public UnusedRuleCheck() {
        super();
    }

    @Override
    public List<Problem> check(ProblemContext context) {
        Set<String> referenced = context.references().stream().map(Reference::to).collect(Collectors.toSet());
        List<Problem> problems = new ArrayList<>();

        for (Artifact artifact : context.artifacts()) {
            if (artifact.type() != ArtifactType.RULE || !artifact.registered() || referenced.contains(artifact.id())) continue;
            problems.add(new Problem(ProblemSeverity.INFO, ProblemCodes.UNUSED_RULE, artifact.id(), null,
                    "No rule set or flow references this rule."));
        }
        return problems;
    }
}
