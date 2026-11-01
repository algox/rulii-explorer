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
 * One bean the application validates with rulii-bean-validation (FR-63): the class, what it
 * declares at class level, its properties and executables, and the rules the library built. Beans
 * are a section of their own in the descriptor and the UI; nothing here is an artifact.
 *
 * @param id            the class name: stable and unique. Kept with {@code include-sources=false},
 *                      because it is the bean's identity (FR-82).
 * @param name          the simple class name.
 * @param packageId     the package.
 * @param kind          class, record, interface or abstract class.
 * @param discovery     how the bean was found.
 * @param source        where it is declared; omitted with {@code include-sources=false}.
 * @param groupSequence the redefined Default group sequence, simple class names; null when not
 *                      redefined.
 * @param materialized  whether the library built the bean's rules; false with a
 *                      {@code BEAN_NOT_MATERIALIZED} problem, and then the rule ids are null.
 * @param flow          the bean's validation flow.
 * @param constraints   the class-level constraints, in hierarchy order.
 * @param validations   the class-level {@code @Validate} declarations.
 * @param expressions   the class-level {@code @Expr} declarations.
 * @param binds         the class-level {@code @Bind} declarations, inherited ones included.
 * @param properties    the properties, in the library's order.
 * @param executables   the constrained methods, constructors and getters, by key.
 * @param rules         the rules the library materialized for the class and its properties.
 *
 * @author Algorithmx Development Team
 * @since 1.1
 */
public record BeanInfo(String id, String name, String packageId, BeanKind kind, Discovery discovery, Source source,
                       List<String> groupSequence, boolean materialized, FlowRef flow, List<ConstraintInfo> constraints,
                       List<ValidationInfo> validations, List<ExprInfo> expressions, List<ContextBindInfo> binds,
                       List<PropertyInfo> properties, List<ExecutableInfo> executables, List<BeanRule> rules) {

    public BeanInfo {
        groupSequence = groupSequence == null ? null : Lists.copy(groupSequence);
        constraints = Lists.copy(constraints);
        validations = Lists.copy(validations);
        expressions = Lists.copy(expressions);
        binds = Lists.copy(binds);
        properties = Lists.copy(properties);
        executables = Lists.copy(executables);
        rules = Lists.copy(rules);
    }
}
