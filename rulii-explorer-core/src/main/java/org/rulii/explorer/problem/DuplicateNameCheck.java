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
import java.util.Map;
import java.util.TreeMap;

/**
 * Two artifacts with the same own name (warning): confusing in listings, and a by-name lookup
 * can only mean one of them.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class DuplicateNameCheck implements ProblemCheck {

    public DuplicateNameCheck() {
        super();
    }

    @Override
    public List<Problem> check(ProblemContext context) {
        Map<String, List<Artifact>> byName = new TreeMap<>();
        for (Artifact artifact : context.artifacts()) {
            if (artifact.name() != null) byName.computeIfAbsent(artifact.name(), n -> new ArrayList<>()).add(artifact);
        }

        List<Problem> problems = new ArrayList<>();
        byName.forEach((name, artifacts) -> {
            if (artifacts.size() < 2) return;
            List<String> ids = artifacts.stream().map(Artifact::id).sorted().toList();
            for (String id : ids) {
                problems.add(new Problem(ProblemSeverity.WARNING, ProblemCodes.DUPLICATE_NAME, id, null,
                        "The name '" + name + "' is used by " + ids.size() + " artifacts: " + String.join(", ", ids) + "."));
            }
        });
        return problems;
    }
}
