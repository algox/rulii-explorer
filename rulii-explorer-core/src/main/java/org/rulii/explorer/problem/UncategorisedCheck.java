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
import org.rulii.explorer.descriptor.Problem;
import org.rulii.explorer.descriptor.ProblemSeverity;

import java.util.ArrayList;
import java.util.List;

/**
 * Info: a registered artifact without a category, once the application uses categories at all.
 * An application that never categorised anything gets no problems from this check, so adopting
 * categories is what makes the gaps visible. Inline members and targets are skipped: they are
 * shown under the artifact that declares them.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
public final class UncategorisedCheck implements ProblemCheck {

    public UncategorisedCheck() {
        super();
    }

    @Override
    public List<Problem> check(ProblemContext context) {
        long categorised = context.artifacts().stream().filter(a -> a.category() != null).count();
        if (categorised == 0) return List.of();
        List<Problem> problems = new ArrayList<>();
        for (Artifact artifact : context.artifacts()) {
            if (artifact.category() != null || !artifact.registered()) continue;
            problems.add(new Problem(ProblemSeverity.INFO, ProblemCodes.UNCATEGORISED, artifact.id(), null,
                    "No category, while " + categorised + (categorised == 1 ? " other artifact has one." : " other artifacts have one.")));
        }
        return problems;
    }
}
