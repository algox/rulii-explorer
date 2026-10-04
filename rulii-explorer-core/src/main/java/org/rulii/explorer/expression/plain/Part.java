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

import org.rulii.explorer.descriptor.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * A translated piece of an expression: its tokens plus the bindings read and written in it, and
 * what the translator needs to know about it to phrase its parent (a literal, a logical
 * operator, a binding path). Shared by every translator.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
public final class Part {

    public final List<Token> tokens = new ArrayList<>();
    public final TreeSet<String> reads = new TreeSet<>();
    public final TreeSet<String> writes = new TreeSet<>();
    /** The binding path when the part is one binding reference; null otherwise. */
    public List<String> path;
    /** True for a literal, so a parent can fold it ("-" + "5" becomes "-5"). */
    public boolean literal;
    /** "and" or "or" when the part is a logical operation, so mixed grouping stays visible. */
    public String logicalOperator;
    public boolean raw;

    public Part() {
        super();
    }

    public static Part of(Token token) {
        Part part = new Part();
        part.tokens.add(token);
        return part;
    }

    public static Part raw(String text) {
        Part part = Part.of(Token.raw(text));
        part.raw = true;
        return part;
    }

    public static Part binding(List<String> path) {
        Part part = Part.of(Token.binding(path, Phrases.humanize(path)));
        part.path = path;
        if (!path.isEmpty()) part.reads.add(String.join(".", path));
        return part;
    }

    public static Part literal(String text) {
        Part part = Part.of(Token.literal(text));
        part.literal = true;
        return part;
    }

    public Part add(Token token) {
        tokens.add(token);
        return this;
    }

    public Part add(Part other) {
        tokens.addAll(other.tokens);
        reads.addAll(other.reads);
        writes.addAll(other.writes);
        raw |= other.raw;
        return this;
    }

    /** Takes over the reads, writes and rawness of another part without its tokens. */
    public Part absorb(Part other) {
        reads.addAll(other.reads);
        writes.addAll(other.writes);
        raw |= other.raw;
        return this;
    }

    public boolean isNullLiteral() {
        return literal && tokens.size() == 1 && "nothing".equals(tokens.get(0).text());
    }

    public boolean isBooleanLiteral() {
        return literal && tokens.size() == 1 && ("true".equals(tokens.get(0).text()) || "false".equals(tokens.get(0).text()));
    }

    public String text() {
        StringBuilder sb = new StringBuilder();
        for (Token token : tokens) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(token.text());
        }
        return sb.toString();
    }

    /**
     * "not X": when X reads "... is ..." the negation moves inside ("items is not empty"),
     * otherwise it is prefixed.
     */
    public static Part negate(Part operand) {
        Part part = new Part().absorb(operand);
        for (int i = 0; i < operand.tokens.size(); i++) {
            Token token = operand.tokens.get(i);
            boolean phrase = Token.OP.equals(token.t()) || Token.CALL.equals(token.t());
            if (phrase && (token.text().equals("is") || token.text().startsWith("is "))) {
                for (int j = 0; j < operand.tokens.size(); j++) {
                    part.tokens.add(j == i ? new Token(token.t(), "is not" + token.text().substring(2), null, null, null) : operand.tokens.get(j));
                }
                return part;
            }
        }
        return part.add(Token.op("not")).add(operand);
    }

    /** Adds an operand of a logical operation; mixed and/or keeps its grouping visible with parentheses. */
    public static void addOperand(Part into, Part operand, String parentLogical) {
        if (parentLogical != null && operand.logicalOperator != null && !parentLogical.equals(operand.logicalOperator)) {
            into.add(Token.op("(")).add(operand).add(Token.op(")"));
        } else {
            into.add(operand);
        }
    }

    /** Appends arguments separated by commas. */
    public static void appendArguments(Part part, List<Part> args) {
        for (int a = 0; a < args.size(); a++) {
            if (a > 0) part.add(Token.op(","));
            part.add(args.get(a));
        }
    }
}
