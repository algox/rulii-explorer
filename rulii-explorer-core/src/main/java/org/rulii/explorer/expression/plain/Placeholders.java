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
package org.rulii.explorer.expression.plain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code ${key:default}} placeholders of a script, taken out before parsing and never
 * resolved (NFR-4). Each one is replaced by a variable the parser accepts: {@code #__ph0} for SpEL,
 * {@code __ph0} for JavaScript; the translator turns that variable back into a placeholder token,
 * and {@link #restore(String)} puts the original text back into raw slices.
 *
 * @author Max Arulananthan
 * @since 1.0
 *
 */
public final class Placeholders {

    public static final String PREFIX = "__ph";
    private static final Pattern PLACEHOLDER = Pattern.compile("(?<!\\\\)\\$\\{\\s*([^}:]+?)\\s*(?::([^}]*))?}");

    public record Placeholder(String key, String defaultValue, String original) {
    }

    /**
     * A placeholder and where it sits in the source text.
     *
     * @param start        the index of the {@code $}.
     * @param end          the index after the closing brace.
     * @param key          the property key, trimmed.
     * @param defaultValue the default as written; null when none.
     */
    public record Span(int start, int end, String key, String defaultValue) {
    }

    /**
     * Every placeholder of a text, in source order. Escaped ones ({@code \${...}}) are not placeholders.
     *
     * @param sourceText the script as written; may be null.
     * @return the spans; empty when there are none.
     */
    public static List<Span> find(String sourceText) {
        if (sourceText == null || !sourceText.contains("${")) return List.of();
        List<Span> found = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(sourceText);
        while (matcher.find()) {
            found.add(new Span(matcher.start(), matcher.end(), matcher.group(1), matcher.group(2)));
        }
        return Collections.unmodifiableList(found);
    }

    private final String rewritten;
    private final List<Placeholder> placeholders;
    private final Pattern variable;

    private Placeholders(String rewritten, List<Placeholder> placeholders, String marker) {
        super();
        this.rewritten = rewritten;
        this.placeholders = Collections.unmodifiableList(placeholders);
        this.variable = Pattern.compile(Pattern.quote(marker) + "(\\d+)");
    }

    /** The SpEL form: placeholders become {@code #__phN} variables. */
    public static Placeholders of(String sourceText) {
        return of(sourceText, "#" + PREFIX);
    }

    /**
     * @param sourceText the script as written.
     * @param marker     the text put in place of each placeholder, followed by its index: the
     *                   variable syntax of the language, such as {@code #__ph} or {@code __ph}.
     */
    public static Placeholders of(String sourceText, String marker) {
        List<Placeholder> found = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(sourceText);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            found.add(new Placeholder(matcher.group(1), matcher.group(2), matcher.group()));
            matcher.appendReplacement(out, Matcher.quoteReplacement(marker + (found.size() - 1)));
        }
        matcher.appendTail(out);
        return new Placeholders(out.toString(), found, marker);
    }

    /** The script with every placeholder replaced by its variable. */
    public String rewritten() {
        return rewritten;
    }

    /**
     * @param variableName a variable name without any language prefix, such as {@code __ph0}.
     * @return the placeholder the variable stands for, or null when it is an ordinary variable.
     */
    public Placeholder forVariable(String variableName) {
        if (variableName == null || !variableName.startsWith(PREFIX)) return null;
        try {
            int index = Integer.parseInt(variableName.substring(PREFIX.length()));
            return index >= 0 && index < placeholders.size() ? placeholders.get(index) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A slice of the rewritten script with the original placeholders put back. */
    public String restore(String rewrittenSlice) {
        Matcher matcher = variable.matcher(rewrittenSlice);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            Placeholder placeholder = forVariable(PREFIX + matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(placeholder != null ? placeholder.original() : matcher.group()));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
