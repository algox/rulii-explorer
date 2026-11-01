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
import org.rulii.explorer.descriptor.BeanInfo;
import org.rulii.explorer.descriptor.Commands;
import org.rulii.explorer.descriptor.CrossParameterInfo;
import org.rulii.explorer.descriptor.ExecutableInfo;
import org.rulii.explorer.descriptor.ParameterInfo;
import org.rulii.explorer.descriptor.Problem;
import org.rulii.explorer.descriptor.ProblemSeverity;
import org.rulii.explorer.descriptor.PropertyInfo;
import org.rulii.explorer.descriptor.Resolution;
import org.rulii.explorer.descriptor.ReturnValueInfo;
import org.rulii.explorer.descriptor.Target;
import org.rulii.explorer.descriptor.TargetKind;
import org.rulii.explorer.descriptor.ValidationInfo;
import org.rulii.explorer.descriptor.ValidationTarget;

import java.util.ArrayList;
import java.util.List;

/**
 * A by-name or by-class run target that resolves to nothing now (error), and a by-name target
 * that matches an artifact own name but not its registry name (warning): the bean
 * {@code rangeCheckRule} holds a rule named {@code RangeCheckRule}, and lookups use bean names.
 * A bean's {@code @Validate} names are by-name targets too (1.1): the library looks them up when
 * the bean is validated, so a name nothing is registered under fails then.
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
                problems.add(problem(context, artifact.id(), path, target));
            });
        }

        if (context.beans() != null) {
            for (BeanInfo bean : context.beans()) {
                validations(context, bean, problems);
            }
        }

        return problems;
    }

    private Problem problem(ProblemContext context, String artifactId, String path, Target target) {
        if (target.kind() == TargetKind.BY_NAME) return byName(context, artifactId, path, target.name());

        return new Problem(ProblemSeverity.ERROR, ProblemCodes.UNRESOLVED_TARGET, artifactId, path,
                "Runs a rule of class " + target.className() + ", but no registered rule has that class.");
    }

    private Problem byName(ProblemContext context, String artifactId, String path, String name) {
        List<String> sameOwnName = context.artifacts().stream()
                .filter(a -> name.equals(a.name()) && !name.equals(a.id()))
                .map(Artifact::id)
                .sorted()
                .toList();

        if (!sameOwnName.isEmpty()) {
            return new Problem(ProblemSeverity.WARNING, ProblemCodes.NAME_MISMATCH_LOOKUP, artifactId, path,
                    "Looks up '" + name + "' by name, but the registry name of that artifact is '"
                            + sameOwnName.get(0) + "'. Lookups use registry names.");
        }

        return new Problem(ProblemSeverity.ERROR, ProblemCodes.UNRESOLVED_TARGET, artifactId, path,
                "Runs '" + name + "', but nothing with that name is registered.");
    }

    // -------------------------------------------------------------------------------------------
    // Beans: every @Validate, at its path
    // -------------------------------------------------------------------------------------------

    private void validations(ProblemContext context, BeanInfo bean, List<Problem> problems) {
        validations(context, bean.id(), "", bean.validations(), problems);

        for (PropertyInfo property : bean.properties()) {
            validations(context, bean.id(), "properties." + property.name() + ".", property.validations(), problems);
        }

        for (ExecutableInfo executable : bean.executables()) {
            String prefix = "executables." + executable.key() + ".";

            for (ParameterInfo parameter : executable.parameters()) {
                validations(context, bean.id(), prefix + "parameters[" + parameter.index() + "].", parameter.validations(), problems);
            }

            CrossParameterInfo cross = executable.crossParameter();
            if (cross != null) validations(context, bean.id(), prefix + "crossParameter.", cross.validations(), problems);

            ReturnValueInfo returnValue = executable.returnValue();
            if (returnValue != null) validations(context, bean.id(), prefix + "returnValue.", returnValue.validations(), problems);
        }
    }

    private void validations(ProblemContext context, String beanId, String prefix, List<ValidationInfo> validations, List<Problem> problems) {
        for (int i = 0; i < validations.size(); i++) {
            String path = prefix + "validations[" + i + "]";

            for (ValidationTarget target : validations.get(i).targets()) {
                if (target.artifactId() == null) problems.add(byName(context, beanId, path, target.name()));
            }
        }
    }
}
