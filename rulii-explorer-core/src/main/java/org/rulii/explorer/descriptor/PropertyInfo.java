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
 * One property of a bean (FR-64): what it declares, in declaration order.
 *
 * @param name              the property name.
 * @param type              the class name of the property's type.
 * @param genericType       the generic type as a string, when it differs from {@code type}.
 * @param declaredOn        field, getter or record component (the primary declaration).
 * @param declaringClass    the class that declares it, when it is not the bean (inherited);
 *                          omitted with {@code include-sources=false}.
 * @param source            where it is declared; omitted with {@code include-sources=false}.
 * @param constraints       the constraints, in declaration order.
 * @param validations       the {@code @Validate} declarations.
 * @param expressions       the {@code @Expr} declarations.
 * @param cascade           the cascade, when {@code @Valid}; null otherwise.
 * @param containerElements the constrained or cascaded container elements.
 *
 * @author Algorithmx Development Team
 * @since 1.1
 */
public record PropertyInfo(String name, String type, String genericType, DeclaredOn declaredOn, String declaringClass,
                           Source source, List<ConstraintInfo> constraints, List<ValidationInfo> validations,
                           List<ExprInfo> expressions, CascadeInfo cascade, List<ContainerElementInfo> containerElements) {

    public PropertyInfo {
        constraints = Lists.copy(constraints);
        validations = Lists.copy(validations);
        expressions = Lists.copy(expressions);
        containerElements = Lists.copy(containerElements);
    }
}
