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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The placeholder pre-pass: {@code ${key:default}} is not SpEL, so each placeholder becomes a
 * synthetic variable ({@code #__ph0}) before parsing and is mapped back to a placeholder token
 * after. Values are never resolved (NFR-3). An escaped {@code \${...}} is left alone.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
final class Placeholders {

    static final String PREFIX = "__ph";

    private static final Pattern PLACEHOLDER = Pattern.compile("(?<!\\\\)\\$\\{\\s*([^}:]+?)\\s*(?::([^}]*))?}");
    private static final Pattern VARIABLE = Pattern.compile("#" + PREFIX + "(\\d+)");

    /** One placeholder: its key, its default as written (null when none) and its original text. */
    record Placeholder(String key, String defaultValue, String original) {
    }

    private final String rewritten;
    private final List<Placeholder> placeholders;

    private Placeholders(String rewritten, List<Placeholder> placeholders) {
        super();
        this.rewritten = rewritten;
        this.placeholders = Collections.unmodifiableList(placeholders);
    }

    static Placeholders of(String sourceText) {
        List<Placeholder> found = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(sourceText);
        StringBuilder out = new StringBuilder();

        while (matcher.find()) {
            found.add(new Placeholder(matcher.group(1), matcher.group(2), matcher.group()));
            matcher.appendReplacement(out, Matcher.quoteReplacement("#" + PREFIX + (found.size() - 1)));
        }
        matcher.appendTail(out);

        return new Placeholders(out.toString(), found);
    }

    /** The text with placeholders replaced by synthetic variables; what the parser sees. */
    String rewritten() {
        return rewritten;
    }

    /**
     * @param variableName a SpEL variable name.
     * @return the placeholder the variable stands for, or null when it is an ordinary variable.
     */
    Placeholder forVariable(String variableName) {
        if (variableName == null || !variableName.startsWith(PREFIX)) return null;
        try {
            int index = Integer.parseInt(variableName.substring(PREFIX.length()));
            return index >= 0 && index < placeholders.size() ? placeholders.get(index) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Puts the original placeholder text back into a slice of the rewritten text. */
    String restore(String rewrittenSlice) {
        Matcher matcher = VARIABLE.matcher(rewrittenSlice);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            Placeholder placeholder = forVariable(PREFIX + matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(placeholder != null ? placeholder.original() : matcher.group()));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
