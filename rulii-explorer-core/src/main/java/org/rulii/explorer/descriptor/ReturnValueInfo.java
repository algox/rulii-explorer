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
 * The return value of a constrained executable (FR-67), described as a property is, plus the
 * {@code @Bind} declarations that apply to the return-value side.
 *
 * @param type              the class name of the return type.
 * @param genericType       the generic type as a string, when it differs from {@code type}.
 * @param constraints       the constraints, in declaration order.
 * @param validations       the {@code @Validate} declarations.
 * @param expressions       the {@code @Expr} declarations.
 * @param cascade           the cascade, when {@code @Valid}; null otherwise.
 * @param containerElements the constrained or cascaded container elements.
 * @param binds             the {@code @Bind} declarations applying to the return value.
 *
 * @author Algorithmx Development Team
 * @since 1.1
 */
public record ReturnValueInfo(String type, String genericType, List<ConstraintInfo> constraints,
                              List<ValidationInfo> validations, List<ExprInfo> expressions, CascadeInfo cascade,
                              List<ContainerElementInfo> containerElements, List<ContextBindInfo> binds) {

    public ReturnValueInfo {
        constraints = Lists.copy(constraints);
        validations = Lists.copy(validations);
        expressions = Lists.copy(expressions);
        containerElements = Lists.copy(containerElements);
        binds = Lists.copy(binds);
    }
}
