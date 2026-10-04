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
package org.rulii.explorer.expression;

import org.rulii.explorer.expression.script.JavaExpressionAnalyzer;
import org.rulii.explorer.expression.script.JsExpressionAnalyzer;
import org.rulii.explorer.expression.spel.SpelExpressionAnalyzer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The analyzers available to a descriptor build, tried in order.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
public final class ExpressionAnalyzers {

    private final List<ExpressionAnalyzer> analyzers;

    public ExpressionAnalyzers(List<ExpressionAnalyzer> analyzers) {
        super();
        this.analyzers = analyzers == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(analyzers));
    }

    /** No analyzers: every script is raw text. */
    public static ExpressionAnalyzers none() {
        return new ExpressionAnalyzers(List.of());
    }

    /**
     * The analyzers the explorer ships with: plain English for SpEL ({@code el}), JavaScript
     * ({@code js}), and a binding scan (reads and writes, no translation) for Java.
     */
    public static ExpressionAnalyzers defaults() {
        return new ExpressionAnalyzers(List.of(new SpelExpressionAnalyzer(), new JsExpressionAnalyzer(), new JavaExpressionAnalyzer(), new BindingScanAnalyzer()));
    }

    /**
     * The first analyzer that supports the language.
     *
     * @param language the script language; may be null.
     * @return the analyzer, or empty when no analyzer handles the language.
     */
    public Optional<ExpressionAnalyzer> forLanguage(String language) {
        if (language == null) return Optional.empty();
        return analyzers.stream().filter(analyzer -> analyzer.supports(language)).findFirst();
    }

    /**
     * Analyses a script when an analyzer handles its language.
     *
     * @param language   the script language.
     * @param sourceText the script text.
     * @return the analysis, or empty when the language is not analysed.
     */
    public Optional<ExpressionAnalysis> analyze(String language, String sourceText) {
        return forLanguage(language).map(analyzer -> analyzer.analyze(sourceText));
    }

    public List<ExpressionAnalyzer> list() {
        return analyzers;
    }
}
