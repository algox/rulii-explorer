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
package org.rulii.explorer.expression.spel;

import org.rulii.explorer.descriptor.Token;
import org.rulii.explorer.expression.plain.Part;
import org.rulii.explorer.expression.plain.Phrases;
import org.rulii.explorer.expression.plain.Placeholders;
import org.rulii.explorer.expression.ExpressionAnalysis;
import org.rulii.explorer.expression.ExpressionAnalyzer;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain English for rulii-spring's SpEL scripts (language {@code el}): a placeholder pre-pass,
 * an AST walk through the phrase book, and raw fallback for anything else (SOLUTION §6).
 *
 * <p>Deterministic and never guessing: {@code #ctx.order.total >= ${order.minTotal:100}} becomes
 * "order total is at least order.minTotal (default 100)", reading {@code order.total}. A script
 * that does not parse yields {@link ExpressionAnalysis#unparsed()}.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
public final class SpelExpressionAnalyzer implements ExpressionAnalyzer {

    /** The language name rulii-spring registers SpEL under. */
    public static final String LANGUAGE = "el";

    private final SpelExpressionParser parser = new SpelExpressionParser();

    public SpelExpressionAnalyzer() {
        super();
    }

    @Override
    public boolean supports(String language) {
        return LANGUAGE.equalsIgnoreCase(language) || "spel".equalsIgnoreCase(language);
    }

    @Override
    public ExpressionAnalysis analyze(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) return ExpressionAnalysis.unparsed();

        Placeholders placeholders = Placeholders.of(sourceText.strip());
        SpelExpression expression;
        try {
            expression = parser.parseRaw(placeholders.rewritten());
        } catch (ParseException | IllegalStateException e) {
            return ExpressionAnalysis.unparsed();
        }

        Part part = new SpelTranslator(placeholders).translate(expression.getAST());
        boolean complete = part.tokens.stream().noneMatch(token -> Token.RAW.equals(token.t()));
        return new ExpressionAnalysis(complete, new ArrayList<>(part.tokens), new ArrayList<>(part.reads),
                new ArrayList<>(part.writes));
    }

}
