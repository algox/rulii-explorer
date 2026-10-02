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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tokens of a JavaScript or Java script: identifiers, keywords, numbers, strings, template
 * literals, regular expressions and punctuators, with comments and whitespace dropped. Each
 * token knows whether a line break preceded it, which is what statement separation needs.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
final class ScriptLexer {

    enum Kind { IDENT, KEYWORD, NUMBER, STRING, TEMPLATE, REGEX, PUNCT, EOF }

    /**
     * @param text  the token as written; for a string, the unescaped value; for a number, without a Java suffix.
     * @param start the offset of the first character in the script.
     * @param end   the offset after the last character.
     */
    record Tok(Kind kind, String text, int start, int end, boolean newlineBefore) {

        boolean is(String punctuatorOrKeyword) {
            return (kind == Kind.PUNCT || kind == Kind.KEYWORD) && text.equals(punctuatorOrKeyword);
        }
    }

    static final Set<String> KEYWORDS = Set.of(
            "true", "false", "null", "undefined", "this", "new", "typeof", "void", "delete", "in", "instanceof",
            "var", "let", "const", "return", "if", "else", "function", "class", "for", "while", "do", "switch",
            "case", "break", "continue", "try", "catch", "finally", "throw", "yield", "await", "async", "of",
            "default", "import", "export", "super", "with", "debugger", "extends", "static", "final");

    /** Longest first, so the scan can take the first that matches. */
    private static final String[] PUNCTUATORS = {
            ">>>=", "...", "===", "!==", "**=", "<<=", ">>=", ">>>", "&&=", "||=", "??=",
            "=>", "->", "::", "==", "!=", "<=", ">=", "&&", "||", "??", "?.", "++", "--", "+=", "-=", "*=", "/=", "%=", "**", "<<", ">>", "&=", "|=", "^=",
            "{", "}", "(", ")", "[", "]", ".", ",", ";", ":", "?", "+", "-", "*", "/", "%", "=", "<", ">", "!", "&", "|", "^", "~", "@"};

    private final String text;
    private final Dialect dialect;
    private final List<Tok> out = new ArrayList<>();
    private int pos;
    private boolean newline;

    private ScriptLexer(String text, Dialect dialect) {
        super();
        this.text = text;
        this.dialect = dialect;
    }

    static List<Tok> lex(String text, Dialect dialect) throws ScriptParser.SyntaxError {
        ScriptLexer lexer = new ScriptLexer(text, dialect);
        lexer.run();
        return lexer.out;
    }

    private void run() throws ScriptParser.SyntaxError {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == '\n') { newline = true; pos++; }
            else if (Character.isWhitespace(c)) pos++;
            else if (c == '/' && peek(1) == '/') skipLineComment();
            else if (c == '/' && peek(1) == '*') skipBlockComment();
            else if (isIdentStart(c)) identifier();
            else if (Character.isDigit(c) || (c == '.' && Character.isDigit(peek(1)))) number();
            else if (c == '\'' || c == '"') string(c);
            else if (c == '`' && dialect == Dialect.JAVASCRIPT) template();
            else if (c == '/' && dialect == Dialect.JAVASCRIPT && regexAllowed()) regex();
            else punctuator();
        }
        out.add(new Tok(Kind.EOF, "", text.length(), text.length(), newline));
    }

    private char peek(int ahead) {
        int i = pos + ahead;
        return i < text.length() ? text.charAt(i) : '\0';
    }

    private void add(Kind kind, String value, int start) {
        out.add(new Tok(kind, value, start, pos, newline));
        newline = false;
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private void skipLineComment() {
        while (pos < text.length() && text.charAt(pos) != '\n') pos++;
    }

    private void skipBlockComment() throws ScriptParser.SyntaxError {
        int end = text.indexOf("*/", pos + 2);
        if (end < 0) throw new ScriptParser.SyntaxError("unterminated comment");
        if (text.substring(pos, end).indexOf('\n') >= 0) newline = true;
        pos = end + 2;
    }

    private void identifier() {
        int start = pos;
        while (pos < text.length() && isIdentPart(text.charAt(pos))) pos++;
        String word = text.substring(start, pos);
        add(KEYWORDS.contains(word) ? Kind.KEYWORD : Kind.IDENT, word, start);
    }

    private void number() {
        int start = pos;
        if (text.charAt(pos) == '0' && (peek(1) == 'x' || peek(1) == 'X')) {
            pos += 2;
            while (pos < text.length() && Character.isLetterOrDigit(text.charAt(pos))) pos++;
            add(Kind.NUMBER, text.substring(start, pos), start);
            return;
        }
        while (pos < text.length() && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.' || text.charAt(pos) == '_')) pos++;
        if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
            pos++;
            if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) pos++;
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
        }
        String value = text.substring(start, pos);
        // A Java suffix (200L, 0.05d, 1f) says the type, not the value
        if (dialect == Dialect.JAVA && pos < text.length() && "lLfFdD".indexOf(text.charAt(pos)) >= 0 && !isIdentPart(peek(1))) pos++;
        add(Kind.NUMBER, value, start);
    }

    private void string(char quote) throws ScriptParser.SyntaxError {
        int start = pos++;
        StringBuilder value = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == quote) { pos++; add(Kind.STRING, value.toString(), start); return; }
            if (c == '\n') break;
            if (c == '\\' && pos + 1 < text.length()) {
                char e = text.charAt(++pos);
                value.append(switch (e) { case 'n' -> '\n'; case 't' -> '\t'; case 'r' -> '\r'; default -> e; });
                pos++;
            } else {
                value.append(c);
                pos++;
            }
        }
        throw new ScriptParser.SyntaxError("unterminated string");
    }

    private void template() throws ScriptParser.SyntaxError {
        int start = pos++;
        int braces = 0;
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == '\\') { pos += 2; continue; }
            if (braces == 0 && c == '`') { pos++; add(Kind.TEMPLATE, text.substring(start, pos), start); return; }
            if (c == '$' && peek(1) == '{') { braces++; pos += 2; continue; }
            if (braces > 0 && c == '}') braces--;
            if (c == '\n') newline = true;
            pos++;
        }
        throw new ScriptParser.SyntaxError("unterminated template literal");
    }

    /** A slash starts a regular expression unless it follows something a division could apply to. */
    private boolean regexAllowed() {
        if (out.isEmpty()) return true;
        Tok prev = out.get(out.size() - 1);
        return switch (prev.kind) {
            case IDENT, NUMBER, STRING, TEMPLATE, REGEX -> false;
            case KEYWORD -> !Set.of("true", "false", "null", "undefined", "this").contains(prev.text);
            case PUNCT -> !(prev.text.equals(")") || prev.text.equals("]") || prev.text.equals("}"));
            default -> true;
        };
    }

    private void regex() throws ScriptParser.SyntaxError {
        int start = pos++;
        boolean inClass = false;
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == '\\') { pos += 2; continue; }
            if (c == '\n') break;
            if (c == '[') inClass = true;
            else if (c == ']') inClass = false;
            else if (c == '/' && !inClass) {
                pos++;
                while (pos < text.length() && Character.isLetter(text.charAt(pos))) pos++;
                add(Kind.REGEX, text.substring(start, pos), start);
                return;
            }
            pos++;
        }
        throw new ScriptParser.SyntaxError("unterminated regular expression");
    }

    private void punctuator() throws ScriptParser.SyntaxError {
        for (String p : PUNCTUATORS) {
            if (text.startsWith(p, pos)) {
                int start = pos;
                pos += p.length();
                add(Kind.PUNCT, p, start);
                return;
            }
        }
        throw new ScriptParser.SyntaxError("unexpected character '" + text.charAt(pos) + "'");
    }
}
