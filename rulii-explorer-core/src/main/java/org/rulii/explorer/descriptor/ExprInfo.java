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
 * One {@code @Expr} as declared (FR-64): a script condition over the bean's bindings, shown in
 * plain English and raw like any other expression (FR-17).
 *
 * @param expression the condition, analysed when its language has an analyzer.
 * @param errorCode  the error code as declared.
 * @param message    the message template as declared; null when the default applies.
 * @param severity   the severity as declared.
 * @param groups     the groups, simple class names; empty for Default.
 * @param applyTo    the constraint target, as declared ({@code IMPLICIT} unless set).
 * @param skipNull   whether a null value passes without evaluation.
 * @param ruleId     the bean rule's id; null when the bean did not materialize.
 *
 * @author Algorithmx Development Team
 * @since 1.1
 */
public record ExprInfo(Expression expression, String errorCode, String message, String severity, List<String> groups,
                       String applyTo, boolean skipNull, String ruleId) {

    public ExprInfo {
        groups = Lists.copy(groups);
    }
}
