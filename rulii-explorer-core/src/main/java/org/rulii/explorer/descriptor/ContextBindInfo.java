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

/**
 * One {@code @Bind} as declared on a bean or an executable (FR-68): a name the library binds
 * for the rules of that bean, from a literal or an expression. Not the {@link BindInfo} of a
 * flow's {@code bind} command.
 *
 * @param name       the binding name.
 * @param type       the declared type's class name; {@code java.lang.Object} when none.
 * @param kind       literal or expression.
 * @param literal    the literal as declared; null for an expression.
 * @param expression the expression, analysed when its language has an analyzer; null for a
 *                   literal.
 * @param applyTo    the constraint target on an executable ({@code IMPLICIT} unless set); null
 *                   at class level.
 * @param declaredOn the class that declares it, when inherited; null when declared by the bean
 *                   itself (omitted with {@code include-sources=false}).
 *
 * @author Algorithmx Development Team
 * @since 1.1
 */
public record ContextBindInfo(String name, String type, BindKind kind, String literal, Expression expression,
                              String applyTo, String declaredOn) {
}
