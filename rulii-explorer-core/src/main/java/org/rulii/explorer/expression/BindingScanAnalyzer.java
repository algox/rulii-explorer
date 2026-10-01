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

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the bindings a script reads and writes without translating it (FR-22 for languages the
 * explorer cannot render in plain English). Every rulii script engine hands the script one
 * object, {@code ctx}, so {@code ctx.order.total} is a read and {@code ctx.total = 100} a write,
 * in JavaScript (GraalJS), Java (Janino) and any JSR-223 language alike.
 *
 * <p>The result has no tokens, so the UI shows the script as written (FR-17). String literals
 * and comments are skipped first, so {@code "ctx.x"} inside a string is not a binding.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class BindingScanAnalyzer implements ExpressionAnalyzer {

    /** The languages rulii ships script engines for, besides SpEL. */
    public static final Set<String> DEFAULT_LANGUAGES = Set.of("js", "javascript", "java");

    private static final String BINDINGS = "ctx";
    private static final Pattern REFERENCE = Pattern.compile(
            "(?<![\\w$.])" + BINDINGS + "\\.([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)(\\s*(\\+\\+|--|[-+*/%&|^]?=(?!=)))?");
    private static final Pattern STRINGS_AND_COMMENTS = Pattern.compile(
            "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|`(?:\\\\.|[^`\\\\])*`|//[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);

    private final Set<String> languages;

    /** Scans {@link #DEFAULT_LANGUAGES}. */
    public BindingScanAnalyzer() {
        this(DEFAULT_LANGUAGES);
    }

    /**
     * @param languages the script language names to scan; compared ignoring case.
     */
    public BindingScanAnalyzer(Set<String> languages) {
        super();
        Set<String> lower = new TreeSet<>();
        for (String language : languages) lower.add(language.toLowerCase());
        this.languages = Set.copyOf(lower);
    }

    @Override
    public boolean supports(String language) {
        return language != null && languages.contains(language.toLowerCase());
    }

    @Override
    public ExpressionAnalysis analyze(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) return ExpressionAnalysis.unparsed();

        String code = STRINGS_AND_COMMENTS.matcher(sourceText).replaceAll(" ");
        Set<String> reads = new TreeSet<>();
        Set<String> writes = new TreeSet<>();

        Matcher matcher = REFERENCE.matcher(code);
        while (matcher.find()) {
            String path = matcher.group(1);
            String operator = matcher.group(3);
            if (operator == null) {
                reads.add(path);
            } else {
                writes.add(path);
                if (!"=".equals(operator)) reads.add(path); // +=, ++ and friends read before they write
            }
        }

        return new ExpressionAnalysis(false, List.of(), new java.util.ArrayList<>(reads), new java.util.ArrayList<>(writes));
    }
}
