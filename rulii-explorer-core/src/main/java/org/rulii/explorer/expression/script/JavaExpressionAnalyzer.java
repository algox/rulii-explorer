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
 * Plain English for Java scripts ({@code java}), the language Janino compiles: the same parser
 * and phrase book as JavaScript, in the Java dialect, so typed locals read as "let", casts are
 * transparent, {@code ctx.order.getTotal()} is the binding "order total", and a static call
 * such as {@code ChronoUnit.DAYS.between(a, b)} is shown as written with its arguments
 * translated. A script the parser cannot read, such as a loop, is left untranslated.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
public final class JavaExpressionAnalyzer implements ExpressionAnalyzer {

    public static final String LANGUAGE = "java";

    public JavaExpressionAnalyzer() {
        super();
    }

    @Override
    public boolean supports(String language) {
        return LANGUAGE.equalsIgnoreCase(language) || "janino".equalsIgnoreCase(language);
    }

    @Override
    public ExpressionAnalysis analyze(String sourceText) {
        return ScriptTranslator.analyze(sourceText, Dialect.JAVA);
    }
}
