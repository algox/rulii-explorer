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
package org.rulii.explorer.expression.script;

import org.rulii.explorer.expression.ExpressionAnalysis;
import org.rulii.explorer.expression.ExpressionAnalyzer;

/**
 * Plain English for JavaScript scripts ({@code js}), the language GraalJS runs: the script is
 * parsed by the explorer's own parser for the subset rule scripts use and translated through the
 * shared phrase book, so {@code ctx.order.total >= ${order.minTotal:100}} reads exactly as its
 * SpEL twin does. Placeholders are taken out before parsing and never resolved. What the
 * translator cannot phrase is shown as written and marks the analysis incomplete; what the
 * parser cannot read at all leaves the script untranslated.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
public final class JsExpressionAnalyzer implements ExpressionAnalyzer {

    public static final String LANGUAGE = "js";

    public JsExpressionAnalyzer() {
        super();
    }

    @Override
    public boolean supports(String language) {
        return LANGUAGE.equalsIgnoreCase(language) || "javascript".equalsIgnoreCase(language) || "ecmascript".equalsIgnoreCase(language);
    }

    @Override
    public ExpressionAnalysis analyze(String sourceText) {
        return ScriptTranslator.analyze(sourceText, Dialect.JAVASCRIPT);
    }
}
