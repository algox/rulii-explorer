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

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The translation of one AST node: its tokens, the binding paths it reads and writes, and the
 * binding path it denotes when it is a plain path (so a parent can assign to it or test it for
 * null).
 *
 * @author Max Arulananthan
 * @since 1.0
 */
final class Part {

    final List<Token> tokens = new ArrayList<>();
    final TreeSet<String> reads = new TreeSet<>();
    final TreeSet<String> writes = new TreeSet<>();
    /** The binding path when this part is just a path ({@code order.total}); null otherwise. */
    List<String> path;
    /** Whether the part is a single literal (so lists of literals can collapse). */
    boolean literal;
    /** Whether the part is a logical and/or, for parenthesising mixed and/or. */
    String logicalOperator;
    boolean raw;

    Part() {
        super();
    }

    static Part of(Token token) {
        Part part = new Part();
        part.tokens.add(token);
        return part;
    }

    static Part raw(String text) {
        Part part = Part.of(Token.raw(text));
        part.raw = true;
        return part;
    }

    static Part binding(List<String> path) {
        Part part = Part.of(Token.binding(path, Phrases.humanize(path)));
        part.path = path;
        if (!path.isEmpty()) part.reads.add(String.join(".", path));
        return part;
    }

    static Part literal(String text) {
        Part part = Part.of(Token.literal(text));
        part.literal = true;
        return part;
    }

    Part add(Token token) {
        tokens.add(token);
        return this;
    }

    /** Appends another part's tokens and merges its reads and writes. */
    Part add(Part other) {
        tokens.addAll(other.tokens);
        reads.addAll(other.reads);
        writes.addAll(other.writes);
        raw |= other.raw;
        return this;
    }

    /** Merges reads and writes only, for parts whose tokens are placed by the caller. */
    Part absorb(Part other) {
        reads.addAll(other.reads);
        writes.addAll(other.writes);
        raw |= other.raw;
        return this;
    }

    boolean isNullLiteral() {
        return literal && tokens.size() == 1 && "nothing".equals(tokens.get(0).text());
    }

    boolean isBooleanLiteral() {
        return literal && tokens.size() == 1 && ("true".equals(tokens.get(0).text()) || "false".equals(tokens.get(0).text()));
    }

    String text() {
        StringBuilder sb = new StringBuilder();
        for (Token token : tokens) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(token.text());
        }
        return sb.toString();
    }
}
