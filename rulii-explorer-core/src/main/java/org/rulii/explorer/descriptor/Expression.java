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
 * A condition, action or function as authored. Never missing when the artifact has one: compiled
 * code is shown as {@code compiled} with its signature (FR-15).
 *
 * @param kind      script, compiled or composite.
 * @param language  script language ({@code el}, {@code js} ...); script only.
 * @param text      the script as written, placeholders unresolved (FR-14); script only.
 * @param placeholders the {@code ${key:default}} placeholders of the text in source order, with their compiled values when the application opted in; script only, null when the text has none.
 * @param plain     the plain-English rendering; script only, and only when the language is analysed.
 * @param reads     binding paths read ({@code order.total}); declared parameters for compiled code.
 * @param writes    binding paths written; empty for compiled code, whose writes are unknown.
 * @param signature the method signature ({@code boolean test(Order order)}); compiled only, null when not introspectable.
 * @param operator  how the operands combine ({@code and}, {@code or}, {@code !}, {@code andThen} ...); composite only.
 * @param operands  the parts, in evaluation order; composite only.
 *
 * @author Max Arulananthan
 * @since 1.0
 *
 */
public record Expression(ExpressionKind kind, String language, String text, List<Placeholder> placeholders, Analysis plain, List<String> reads,
                         List<String> writes, String signature, String operator, List<Expression> operands) {

    public Expression {
        reads = Lists.copy(reads);
        writes = Lists.copy(writes);
        operands = operands == null ? null : Lists.copy(operands);
        placeholders = placeholders == null ? null : Lists.copy(placeholders);
    }
}
