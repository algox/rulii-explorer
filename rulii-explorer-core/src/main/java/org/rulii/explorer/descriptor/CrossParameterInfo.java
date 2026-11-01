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
 * What an executable declares across its parameters (FR-67): cross-parameter constraints and
 * the {@code @Validate} and {@code @Expr} declarations that apply to the parameters.
 *
 * @param constraints the cross-parameter constraints, in declaration order.
 * @param validations the {@code @Validate} declarations applying to the parameters.
 * @param expressions the {@code @Expr} declarations applying to the parameters.
 *
 * @author Algorithmx Development Team
 * @since 1.1
 */
public record CrossParameterInfo(List<ConstraintInfo> constraints, List<ValidationInfo> validations, List<ExprInfo> expressions) {

    public CrossParameterInfo {
        constraints = Lists.copy(constraints);
        validations = Lists.copy(validations);
        expressions = Lists.copy(expressions);
    }
}
