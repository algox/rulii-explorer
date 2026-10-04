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

import org.rulii.explorer.expression.script.ScriptLexer.Kind;
import org.rulii.explorer.expression.script.ScriptLexer.Tok;
import org.rulii.explorer.expression.script.ScriptNode.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A recursive-descent parser for the JavaScript and Java that rule scripts are written in:
 * expressions with the usual precedence, and the statements {@code var/let/const} (or a Java
 * type), {@code return}, {@code if/else} and blocks, separated by semicolons or line breaks.
 * Functions and lambdas, object literals, {@code new}, regular expressions and {@code this} are
 * parsed past as {@link Opaque} nodes so the rest of the script still reads; loops,
 * {@code switch}, {@code try}, classes and destructuring are not parsed at all, and the script
 * is shown as written. Java casts become {@link Cast} nodes.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
final class ScriptParser {

    static final class SyntaxError extends Exception {
        SyntaxError(String message) {
            super(message);
        }
    }

    private static final Set<String> ASSIGNMENT = Set.of("=", "+=", "-=", "*=", "/=", "%=", "**=", "&&=", "||=", "??=", "<<=", ">>=", ">>>=", "&=", "|=", "^=");
    private static final Set<String> EQUALITY = Set.of("==", "!=", "===", "!==");
    private static final Set<String> RELATIONAL = Set.of("<", ">", "<=", ">=", "instanceof", "in");
    private static final Set<String> SHIFT = Set.of("<<", ">>", ">>>");
    private static final Set<String> ADDITIVE = Set.of("+", "-");
    private static final Set<String> MULTIPLICATIVE = Set.of("*", "/", "%");
    private static final Set<String> UNARY = Set.of("!", "-", "+", "~", "typeof", "void", "delete");
    private static final Set<String> UNSUPPORTED_STATEMENTS = Set.of("for", "while", "do", "switch", "try", "throw", "class", "break", "continue", "import", "export", "with", "debugger");
    private static final Set<String> PRIMITIVES = Set.of("int", "long", "double", "float", "short", "byte", "char", "boolean");

    private final List<Tok> toks;
    private final Dialect dialect;
    private final String text;
    private int pos;

    private ScriptParser(List<Tok> toks, Dialect dialect, String text) {
        super();
        this.toks = toks;
        this.dialect = dialect;
        this.text = text;
    }

    static Program parse(String text, Dialect dialect) throws SyntaxError {
        ScriptParser parser = new ScriptParser(ScriptLexer.lex(text, dialect), dialect, text);
        List<ScriptNode> statements = new ArrayList<>();
        while (parser.tok().kind() != Kind.EOF) {
            statements.addAll(parser.statement());
            parser.separator();
        }
        return new Program(statements, 0, text.length());
    }

    // ── Tokens ──────────────────────────────────────────────────────────────

    private Tok tok() {
        return toks.get(pos);
    }

    private Tok peek() {
        return peek(1);
    }

    private Tok peek(int ahead) {
        return toks.get(Math.min(pos + ahead, toks.size() - 1));
    }

    private Tok next() {
        Tok t = toks.get(pos);
        if (pos < toks.size() - 1) pos++;
        return t;
    }

    private boolean at(String punctuatorOrKeyword) {
        return tok().is(punctuatorOrKeyword);
    }

    private boolean accept(String punctuatorOrKeyword) {
        if (at(punctuatorOrKeyword)) { next(); return true; }
        return false;
    }

    private Tok expect(String punctuatorOrKeyword) throws SyntaxError {
        if (!at(punctuatorOrKeyword)) throw new SyntaxError("expected '" + punctuatorOrKeyword + "' but found '" + tok().text() + "'");
        return next();
    }

    private int lastEnd() {
        return toks.get(Math.max(0, pos - 1)).end();
    }

    /** Statements end at a semicolon, at a line break, before a closing brace, or at the end. */
    private void separator() throws SyntaxError {
        if (accept(";")) { while (accept(";")) { /* empty statements */ } return; }
        if (tok().kind() == Kind.EOF || at("}") || tok().newlineBefore()) return;
        throw new SyntaxError("unexpected '" + tok().text() + "'");
    }

    // ── Statements ──────────────────────────────────────────────────────────

    private List<ScriptNode> statement() throws SyntaxError {
        Tok t = tok();
        if (t.kind() == Kind.KEYWORD && UNSUPPORTED_STATEMENTS.contains(t.text())) throw new SyntaxError("'" + t.text() + "' is not read");
        if (at("function") || at("async")) throw new SyntaxError("function declarations are not read");
        if (at("{")) return block();
        if (at("var") || at("let") || at("const")) return declaration(next().text());
        if (dialect == Dialect.JAVA) {
            int start = pos;
            while (accept("final")) { /* a modifier */ }
            int last = typeAhead(pos);
            if (last >= 0) {
                Tok name = peek(last + 1 - pos);
                Tok following = peek(last + 2 - pos);
                if (name.kind() == Kind.IDENT && (following.is("=") || following.is(";") || following.is(",") || following.kind() == Kind.EOF || following.is("}") || following.newlineBefore())) {
                    return declaration(type());
                }
            }
            pos = start;
        }
        if (at("return")) return List.of(returnStatement());
        if (at("if")) return List.of(ifStatement());
        if (accept(";")) return List.of();
        return List.of(expression());
    }

    private List<ScriptNode> block() throws SyntaxError {
        expect("{");
        List<ScriptNode> statements = new ArrayList<>();
        while (!at("}")) {
            if (tok().kind() == Kind.EOF) throw new SyntaxError("unterminated block");
            statements.addAll(statement());
            separator();
        }
        expect("}");
        return statements;
    }

    /** {@code let a = 1, b}: the keyword or type has been consumed. */
    private List<ScriptNode> declaration(String kind) throws SyntaxError {
        int start = lastEnd();
        List<ScriptNode> declared = new ArrayList<>();
        do {
            Tok name = tok();
            if (name.kind() != Kind.IDENT) throw new SyntaxError("only simple variable names are read");
            next();
            ScriptNode init = accept("=") ? assignment() : null;
            declared.add(new VarDecl(kind, name.text(), init, declared.isEmpty() ? start : name.start(), lastEnd()));
        } while (accept(","));
        return declared;
    }

    private ScriptNode returnStatement() throws SyntaxError {
        Tok keyword = expect("return");
        boolean bare = at(";") || at("}") || tok().kind() == Kind.EOF || tok().newlineBefore();
        ScriptNode value = bare ? null : expression();
        return new Return(value, keyword.start(), lastEnd());
    }

    private ScriptNode ifStatement() throws SyntaxError {
        Tok keyword = expect("if");
        expect("(");
        ScriptNode test = expression();
        expect(")");
        List<ScriptNode> then = branch();
        List<ScriptNode> otherwise = null;
        if (accept("else")) otherwise = branch();
        return new If(test, then, otherwise, keyword.start(), lastEnd());
    }

    private List<ScriptNode> branch() throws SyntaxError {
        if (at("{")) return block();
        List<ScriptNode> single = statement();
        accept(";");
        return single;
    }

    // ── Java types ──────────────────────────────────────────────────────────

    /**
     * The index of the last token of a type starting at {@code from} ({@code java.util.List<String>[]}),
     * or -1 when no type starts there. Does not move the parser.
     */
    private int typeAhead(int from) {
        int i = from;
        if (toks.get(i).kind() != Kind.IDENT) return -1;
        while (toks.get(i + 1).is(".") && toks.get(Math.min(i + 2, toks.size() - 1)).kind() == Kind.IDENT) i += 2;
        if (toks.get(i + 1).is("<")) {
            int depth = 0;
            int j = i + 1;
            while (j < toks.size()) {
                Tok t = toks.get(j);
                if (t.is("<")) depth++;
                else if (t.is(">")) depth--;
                else if (t.is(">>")) depth -= 2;
                else if (t.kind() == Kind.EOF || !(t.kind() == Kind.IDENT || t.is(",") || t.is(".") || t.is("?") || t.is("[") || t.is("]"))) return -1;
                if (depth <= 0) { i = j; break; }
                j++;
            }
            if (depth > 0) return -1;
        }
        while (toks.get(i + 1).is("[") && toks.get(Math.min(i + 2, toks.size() - 1)).is("]")) i += 2;
        return i;
    }

    /** Consumes a type and returns it as written. */
    private String type() throws SyntaxError {
        int last = typeAhead(pos);
        if (last < 0) throw new SyntaxError("expected a type");
        int start = tok().start();
        int end = toks.get(last).end();
        pos = last + 1;
        return text.substring(start, end);
    }

    /** Whether the parenthesis at the current token opens a cast: {@code (int) x}, {@code (Order) ctx.order}. */
    private boolean isCast() {
        if (dialect != Dialect.JAVA || !at("(")) return false;
        int last = typeAhead(pos + 1);
        if (last < 0 || !toks.get(last + 1).is(")")) return false;
        Tok after = toks.get(Math.min(last + 2, toks.size() - 1));
        boolean primitive = last == pos + 1 && PRIMITIVES.contains(toks.get(last).text());
        return switch (after.kind()) {
            case IDENT, NUMBER, STRING -> true;
            case KEYWORD -> Set.of("true", "false", "null", "new", "this").contains(after.text());
            case PUNCT -> after.is("(") || after.is("!") || after.is("~") || (primitive && (after.is("-") || after.is("+")));
            default -> false;
        };
    }

    // ── Expressions, lowest precedence first ───────────────────────────────

    private ScriptNode expression() throws SyntaxError {
        ScriptNode first = assignment();
        if (at(",")) throw new SyntaxError("comma expressions are not read");
        return first;
    }

    private ScriptNode assignment() throws SyntaxError {
        ScriptNode target = conditional();
        if (tok().kind() == Kind.PUNCT && ASSIGNMENT.contains(tok().text())) {
            if (!(target instanceof Ident || target instanceof Member || target instanceof Index)) throw new SyntaxError("invalid assignment target");
            String op = next().text();
            ScriptNode value = assignment();
            return new Assign(op, target, value, target.start(), value.end());
        }
        return target;
    }

    private ScriptNode conditional() throws SyntaxError {
        ScriptNode test = nullish();
        if (accept("?")) {
            ScriptNode then = assignment();
            expect(":");
            ScriptNode otherwise = assignment();
            return new Cond(test, then, otherwise, test.start(), otherwise.end());
        }
        return test;
    }

    private ScriptNode nullish() throws SyntaxError {
        ScriptNode left = or();
        while (at("??")) { next(); ScriptNode right = or(); left = new Logical("??", left, right, left.start(), right.end()); }
        return left;
    }

    private ScriptNode or() throws SyntaxError {
        ScriptNode left = and();
        while (at("||")) { next(); ScriptNode right = and(); left = new Logical("||", left, right, left.start(), right.end()); }
        return left;
    }

    private ScriptNode and() throws SyntaxError {
        ScriptNode left = bitwise();
        while (at("&&")) { next(); ScriptNode right = bitwise(); left = new Logical("&&", left, right, left.start(), right.end()); }
        return left;
    }

    /** {@code | ^ &} at one level: they are shown raw anyway, so their relative precedence does not matter. */
    private ScriptNode bitwise() throws SyntaxError {
        ScriptNode left = equality();
        while (at("|") || at("^") || at("&")) { String op = next().text(); ScriptNode right = equality(); left = new Binary(op, left, right, left.start(), right.end()); }
        return left;
    }

    private ScriptNode equality() throws SyntaxError {
        ScriptNode left = relational();
        while (tok().kind() == Kind.PUNCT && EQUALITY.contains(tok().text())) { String op = next().text(); ScriptNode right = relational(); left = new Binary(op, left, right, left.start(), right.end()); }
        return left;
    }

    private ScriptNode relational() throws SyntaxError {
        ScriptNode left = shift();
        while ((tok().kind() == Kind.PUNCT || tok().kind() == Kind.KEYWORD) && RELATIONAL.contains(tok().text())) {
            String op = next().text();
            ScriptNode right = "instanceof".equals(op) && dialect == Dialect.JAVA ? typeOperand() : shift();
            left = new Binary(op, left, right, left.start(), right.end());
        }
        return left;
    }

    /** The type after a Java {@code instanceof}, kept opaque. */
    private ScriptNode typeOperand() throws SyntaxError {
        int start = tok().start();
        type();
        return new Opaque("type", start, lastEnd());
    }

    private ScriptNode shift() throws SyntaxError {
        ScriptNode left = additive();
        while (tok().kind() == Kind.PUNCT && SHIFT.contains(tok().text())) { String op = next().text(); ScriptNode right = additive(); left = new Binary(op, left, right, left.start(), right.end()); }
        return left;
    }

    private ScriptNode additive() throws SyntaxError {
        ScriptNode left = multiplicative();
        while (tok().kind() == Kind.PUNCT && ADDITIVE.contains(tok().text())) { String op = next().text(); ScriptNode right = multiplicative(); left = new Binary(op, left, right, left.start(), right.end()); }
        return left;
    }

    private ScriptNode multiplicative() throws SyntaxError {
        ScriptNode left = exponent();
        while (tok().kind() == Kind.PUNCT && MULTIPLICATIVE.contains(tok().text())) { String op = next().text(); ScriptNode right = exponent(); left = new Binary(op, left, right, left.start(), right.end()); }
        return left;
    }

    private ScriptNode exponent() throws SyntaxError {
        ScriptNode base = unary();
        if (at("**")) { next(); ScriptNode power = exponent(); return new Binary("**", base, power, base.start(), power.end()); }
        return base;
    }

    private ScriptNode unary() throws SyntaxError {
        Tok t = tok();
        if ((t.kind() == Kind.PUNCT || t.kind() == Kind.KEYWORD) && UNARY.contains(t.text())) {
            next();
            ScriptNode operand = unary();
            return new Unary(t.text(), operand, t.start(), operand.end());
        }
        if (at("++") || at("--")) {
            next();
            ScriptNode target = unary();
            return new Update(t.text(), target, t.start(), target.end());
        }
        if (isCast()) {
            Tok open = next();
            String type = type();
            expect(")");
            ScriptNode operand = unary();
            return new Cast(type, operand, open.start(), operand.end());
        }
        return postfix();
    }

    private ScriptNode postfix() throws SyntaxError {
        ScriptNode operand = callOrMember();
        if ((at("++") || at("--")) && !tok().newlineBefore()) {
            Tok op = next();
            return new Update(op.text(), operand, operand.start(), op.end());
        }
        return operand;
    }

    private ScriptNode callOrMember() throws SyntaxError {
        ScriptNode node = primary();
        while (true) {
            if (at(".") || at("?.")) {
                next();
                Tok name = tok();
                if (name.kind() != Kind.IDENT && name.kind() != Kind.KEYWORD) throw new SyntaxError("expected a property name");
                next();
                node = new Member(node, name.text(), node.start(), name.end());
            } else if (at("[")) {
                next();
                ScriptNode index = expression();
                Tok close = expect("]");
                node = new Index(node, index, node.start(), close.end());
            } else if (at("(")) {
                next();
                List<ScriptNode> args = new ArrayList<>();
                while (!at(")")) {
                    if (at("...")) throw new SyntaxError("spread arguments are not read");
                    args.add(assignment());
                    if (!accept(",")) break;
                }
                Tok close = expect(")");
                node = new Call(node, args, node.start(), close.end());
            } else if (at("::")) {
                next();
                Tok name = next();
                node = new Opaque("method reference", node.start(), name.end());
            } else {
                return node;
            }
        }
    }

    private ScriptNode primary() throws SyntaxError {
        Tok t = tok();
        switch (t.kind()) {
            case IDENT -> {
                if (peek().is("=>") || peek().is("->")) return arrow(t.start());
                next();
                return new Ident(t.text(), t.start(), t.end());
            }
            case NUMBER -> { next(); return new Num(t.text(), t.start(), t.end()); }
            case STRING -> { next(); return new Str(t.text(), t.start(), t.end()); }
            case TEMPLATE -> { next(); return new Template(t.text(), t.start(), t.end()); }
            case REGEX -> { next(); return new Opaque("regular expression", t.start(), t.end()); }
            case KEYWORD -> {
                switch (t.text()) {
                    case "true" -> { next(); return new Bool(true, t.start(), t.end()); }
                    case "false" -> { next(); return new Bool(false, t.start(), t.end()); }
                    case "null", "undefined" -> { next(); return new Nothing(t.start(), t.end()); }
                    case "this" -> { next(); return new Opaque("this", t.start(), t.end()); }
                    case "new" -> { return construction(); }
                    case "function", "async" -> { return function(); }
                    default -> throw new SyntaxError("unexpected '" + t.text() + "'");
                }
            }
            case PUNCT -> {
                if (at("(")) {
                    if (isArrowParameters()) return arrow(t.start());
                    next();
                    ScriptNode inner = expression();
                    expect(")");
                    return inner;
                }
                if (at("[")) return arrayLiteral();
                if (at("{")) { int end = skipBalanced("{", "}"); return new Opaque("object literal", t.start(), end); }
                throw new SyntaxError("unexpected '" + t.text() + "'");
            }
            default -> throw new SyntaxError("unexpected end of script");
        }
    }

    /** {@code new Type(args)}, {@code new Type<T>()}, {@code new int[n]}, {@code new int[]{...}}: parsed past, kept opaque. */
    private ScriptNode construction() throws SyntaxError {
        Tok keyword = expect("new");
        if (typeAhead(pos) >= 0) type();
        else if (tok().kind() == Kind.IDENT) next();
        else throw new SyntaxError("expected a type after 'new'");
        if (at("(")) skipBalanced("(", ")");
        while (at("[")) skipBalanced("[", "]");
        if (at("{")) skipBalanced("{", "}");
        return new Opaque("new", keyword.start(), lastEnd());
    }

    private ScriptNode arrayLiteral() throws SyntaxError {
        Tok open = expect("[");
        List<ScriptNode> items = new ArrayList<>();
        while (!at("]")) {
            if (at("...")) throw new SyntaxError("spread elements are not read");
            items.add(assignment());
            if (!accept(",")) break;
        }
        Tok close = expect("]");
        return new ArrayLit(items, open.start(), close.end());
    }

    /** Whether the parenthesis at the current token is a lambda's parameter list. */
    private boolean isArrowParameters() {
        int depth = 0;
        for (int i = pos; i < toks.size(); i++) {
            Tok t = toks.get(i);
            if (t.is("(")) depth++;
            else if (t.is(")")) { depth--; if (depth == 0) { Tok after = toks.get(Math.min(i + 1, toks.size() - 1)); return after.is("=>") || after.is("->"); } }
            else if (t.kind() == Kind.EOF) return false;
        }
        return false;
    }

    /** Parses past a lambda: its parameters, the arrow, and a block or expression body. */
    private ScriptNode arrow(int start) throws SyntaxError {
        if (at("(")) skipBalanced("(", ")"); else next();
        if (!accept("=>")) expect("->");
        int end = at("{") ? skipBalanced("{", "}") : assignment().end();
        return new Opaque("function", start, end);
    }

    /** Parses past a function expression: name, parameters and body. */
    private ScriptNode function() throws SyntaxError {
        Tok start = next();
        if (at("function")) next();
        if (tok().kind() == Kind.IDENT) next();
        skipBalanced("(", ")");
        int end = skipBalanced("{", "}");
        return new Opaque("function", start.start(), end);
    }

    /** Skips a bracketed region and returns the offset after its closing bracket. */
    private int skipBalanced(String open, String close) throws SyntaxError {
        expect(open);
        int depth = 1;
        while (depth > 0) {
            Tok t = next();
            if (t.kind() == Kind.EOF) throw new SyntaxError("missing '" + close + "'");
            if (t.is(open)) depth++;
            else if (t.is(close)) depth--;
            if (depth == 0) return t.end();
        }
        return lastEnd();
    }
}
