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
 * One piece of a plain-English rendering of an expression (FR-17).
 *
 * <p>Types: {@code binding} (a binding read, with its path), {@code op} (an operator phrase),
 * {@code literal}, {@code placeholder} (a {@code ${key:default}} placeholder, never its resolved
 * value), {@code call}, {@code keyword}, and {@code raw} (a part that could not be translated,
 * shown as written).
 *
 * @param t            the token type.
 * @param text         the text to show.
 * @param path         for a binding: the segments of the path ({@code ["order", "total"]}); null otherwise.
 * @param key          for a placeholder: the property key; null otherwise.
 * @param defaultValue for a placeholder: the default as written; null when none.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public record Token(String t, String text, List<String> path, String key, String defaultValue) {

    public static final String BINDING = "binding";
    public static final String OP = "op";
    public static final String LITERAL = "literal";
    public static final String PLACEHOLDER = "placeholder";
    public static final String CALL = "call";
    public static final String KEYWORD = "keyword";
    public static final String RAW = "raw";

    public Token {
        path = path == null ? null : Lists.copy(path);
    }

    public static Token binding(List<String> path, String text) {
        return new Token(BINDING, text, path, null, null);
    }

    public static Token op(String text) {
        return new Token(OP, text, null, null, null);
    }

    public static Token literal(String text) {
        return new Token(LITERAL, text, null, null, null);
    }

    public static Token placeholder(String key, String defaultValue) {
        String text = defaultValue != null ? key + " (default " + defaultValue + ")" : key;
        return new Token(PLACEHOLDER, text, null, key, defaultValue);
    }

    public static Token call(String text) {
        return new Token(CALL, text, null, null, null);
    }

    public static Token keyword(String text) {
        return new Token(KEYWORD, text, null, null, null);
    }

    public static Token raw(String text) {
        return new Token(RAW, text, null, null, null);
    }
}
