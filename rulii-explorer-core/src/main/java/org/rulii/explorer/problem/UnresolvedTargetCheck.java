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
import org.rulii.explorer.descriptor.Commands;
import org.rulii.explorer.descriptor.Problem;
import org.rulii.explorer.descriptor.ProblemSeverity;
import org.rulii.explorer.descriptor.Resolution;
import org.rulii.explorer.descriptor.Target;
import org.rulii.explorer.descriptor.TargetKind;

import java.util.ArrayList;
import java.util.List;

/**
 * A by-name or by-class run target that resolves to nothing now (error), and a by-name target
 * that matches an artifact own name but not its registry name (warning): the bean
 * {@code rangeCheckRule} holds a rule named {@code RangeCheckRule}, and lookups use bean names.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
public final class UnresolvedTargetCheck implements ProblemCheck {

    public UnresolvedTargetCheck() {
        super();
    }

    @Override
    public List<Problem> check(ProblemContext context) {
        List<Problem> problems = new ArrayList<>();

        for (Artifact artifact : context.artifacts()) {
            if (artifact.ruleFlow() == null) continue;

            Commands.walk(artifact.ruleFlow(), (path, command) -> {
                Target target = command.target();
                if (target == null || target.resolution() != Resolution.UNRESOLVED) return;
                problems.add(problem(context, artifact, path, target));
            });
        }

        return problems;
    }

    private Problem problem(ProblemContext context, Artifact artifact, String path, Target target) {
        if (target.kind() == TargetKind.BY_NAME) {
            List<String> sameOwnName = context.artifacts().stream()
                    .filter(a -> target.name().equals(a.name()) && !target.name().equals(a.id()))
                    .map(Artifact::id)
                    .sorted()
                    .toList();

            if (!sameOwnName.isEmpty()) {
                return new Problem(ProblemSeverity.WARNING, ProblemCodes.NAME_MISMATCH_LOOKUP, artifact.id(), path,
                        "Looks up '" + target.name() + "' by name, but the registry name of that artifact is '"
                                + sameOwnName.get(0) + "'. Lookups use registry names.");
            }

            return new Problem(ProblemSeverity.ERROR, ProblemCodes.UNRESOLVED_TARGET, artifact.id(), path,
                    "Runs '" + target.name() + "', but nothing with that name is registered.");
        }

        return new Problem(ProblemSeverity.ERROR, ProblemCodes.UNRESOLVED_TARGET, artifact.id(), path,
                "Runs a rule of class " + target.className() + ", but no registered rule has that class.");
    }
}
