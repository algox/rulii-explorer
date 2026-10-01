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
import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.ast.Assign;
import org.springframework.expression.spel.ast.BooleanLiteral;
import org.springframework.expression.spel.ast.CompoundExpression;
import org.springframework.expression.spel.ast.Elvis;
import org.springframework.expression.spel.ast.Indexer;
import org.springframework.expression.spel.ast.InlineList;
import org.springframework.expression.spel.ast.Literal;
import org.springframework.expression.spel.ast.MethodReference;
import org.springframework.expression.spel.ast.NullLiteral;
import org.springframework.expression.spel.ast.OpAnd;
import org.springframework.expression.spel.ast.OpMinus;
import org.springframework.expression.spel.ast.OpOr;
import org.springframework.expression.spel.ast.OpPlus;
import org.springframework.expression.spel.ast.Operator;
import org.springframework.expression.spel.ast.OperatorBetween;
import org.springframework.expression.spel.ast.OperatorNot;
import org.springframework.expression.spel.ast.PropertyOrFieldReference;
import org.springframework.expression.spel.ast.StringLiteral;
import org.springframework.expression.spel.ast.Ternary;
import org.springframework.expression.spel.ast.VariableReference;

import java.util.ArrayList;
import java.util.List;

/**
 * Walks a SpEL AST and renders it in plain English, collecting the bindings it reads and
 * writes. The root object and the {@code #ctx} variable are both the rule context bindings, so
 * {@code age} and {@code #ctx.age} are the same binding read. Anything the phrase book cannot
 * express becomes a {@code raw} token holding its source text (FR-17).
 *
 * @author Max Arulananthan
 * @since 1.0
 */
final class SpelTranslator {

    private static final String BINDINGS_VARIABLE = "ctx";

    private final String text;
    private final Placeholders placeholders;

    SpelTranslator(Placeholders placeholders) {
        super();
        this.placeholders = placeholders;
        this.text = placeholders.rewritten();
    }

    Part translate(SpelNode node) {
        if (node instanceof Literal literal) return literal(literal);
        if (node instanceof PropertyOrFieldReference property) return Part.binding(List.of(property.getName()));
        if (node instanceof VariableReference variable) return variable(variable);
        if (node instanceof CompoundExpression compound) return compound(compound);
        if (node instanceof Assign assign) return assign(assign);
        if (node instanceof OperatorNot not) return not(translate(not.getChild(0)));
        if (node instanceof Ternary ternary) return ternary(ternary);
        if (node instanceof Elvis elvis) return elvis(elvis);
        if (node instanceof InlineList list) return inlineList(list);
        if (node instanceof OperatorBetween between) return between(between);
        if (node instanceof Operator operator) return operator(operator);
        return raw(node);
    }

    // -------------------------------------------------------------------------------------------
    // Leaves
    // -------------------------------------------------------------------------------------------

    private Part literal(Literal literal) {
        if (literal instanceof NullLiteral) return Part.literal("nothing");
        if (literal instanceof BooleanLiteral) return Part.literal(literal.toStringAST());
        if (literal instanceof StringLiteral string) {
            Object value = string.getLiteralValue().getValue();
            return Part.literal("\"" + placeholders.restore(String.valueOf(value)) + "\"");
        }
        return Part.literal(literal.toStringAST());
    }

    private Part variable(VariableReference variable) {
        String name = variableName(variable);
        Placeholders.Placeholder placeholder = placeholders.forVariable(name);
        if (placeholder != null) return Part.of(Token.placeholder(placeholder.key(), placeholder.defaultValue()));
        if (BINDINGS_VARIABLE.equals(name)) return Part.binding(List.of());
        return raw(variable);
    }

    private static String variableName(VariableReference variable) {
        String ast = variable.toStringAST();
        return ast.startsWith("#") ? ast.substring(1) : ast;
    }

    // -------------------------------------------------------------------------------------------
    // Paths, calls and indexes
    // -------------------------------------------------------------------------------------------

    private Part compound(CompoundExpression compound) {
        List<String> path = new ArrayList<>();
        Part receiver = null;
        int i = 0;

        SpelNode first = compound.getChild(0);
        if (first instanceof VariableReference variable) {
            String name = variableName(variable);
            if (!BINDINGS_VARIABLE.equals(name) || placeholders.forVariable(name) != null) return raw(compound);
            i = 1;
        } else if (!(first instanceof PropertyOrFieldReference)) {
            return raw(compound);
        }

        for (; i < compound.getChildCount(); i++) {
            SpelNode child = compound.getChild(i);

            if (child instanceof PropertyOrFieldReference property && receiver == null) {
                path.add(property.getName());
            } else if (child instanceof PropertyOrFieldReference property) {
                // A property of a call or index result: "price of items at 0"
                receiver = new Part().add(Token.call(Phrases.humanize(property.getName()) + " of")).add(receiver);
            } else if (child instanceof MethodReference method) {
                Part target = receiver != null ? receiver : Part.binding(path);
                boolean last = i == compound.getChildCount() - 1;
                Part call = call(target, path, method, last);
                if (call == null) return raw(compound);
                receiver = call;
            } else if (child instanceof Indexer indexer) {
                Part target = receiver != null ? receiver : Part.binding(path);
                Part index = translate(indexer.getChild(0));
                receiver = new Part().add(target).add(Token.op("at")).add(index);
            } else {
                return raw(compound);
            }
        }

        return receiver != null ? receiver : Part.binding(path);
    }

    /**
     * A method call on a binding or on the bindings themselves. Returns null when the call cannot
     * be phrased, so the whole compound is shown raw.
     */
    private Part call(Part target, List<String> path, MethodReference method, boolean last) {
        String name = method.getName();
        List<Part> args = new ArrayList<>();
        for (int a = 0; a < method.getChildCount(); a++) args.add(translate(method.getChild(a)));

        // #ctx.setValue('x', v) / #ctx.bind('x', v) / #ctx.getValue('x'): the bindings' own API.
        if (target.path != null && target.path.isEmpty()) {
            if (("setValue".equals(name) || "bind".equals(name) || "setValueOrBind".equals(name)) && args.size() == 2
                    && args.get(0).literal && last) {
                List<String> written = List.of(unquote(args.get(0).text()));
                Part part = new Part().add(Token.op("set")).add(Token.binding(written, Phrases.humanize(written)))
                        .add(Token.op("to")).add(args.get(1));
                part.writes.add(String.join(".", written));
                return part;
            }
            if ("getValue".equals(name) && args.size() == 1 && args.get(0).literal) {
                return Part.binding(List.of(unquote(args.get(0).text())));
            }
            return null;
        }

        String prefix = Phrases.prefixMethod(name);
        String infix = Phrases.infixMethod(name);
        Part part = new Part();

        if (prefix != null && args.isEmpty()) {
            part.add(Token.call(prefix)).add(target);
        } else if (infix != null) {
            part.add(target).add(Token.call(infix));
            appendArguments(part, args);
        } else {
            Phrases.GenericMethod generic = Phrases.genericMethod(name);
            if (generic.prefix() && args.isEmpty()) {
                part.add(Token.call(generic.phrase())).add(target);
            } else {
                part.add(target).add(Token.call(generic.phrase()));
                appendArguments(part, args);
            }
        }
        return part;
    }

    private static void appendArguments(Part part, List<Part> args) {
        for (int a = 0; a < args.size(); a++) {
            if (a > 0) part.add(Token.op(","));
            part.add(args.get(a));
        }
    }

    private static String unquote(String literal) {
        return literal.length() >= 2 && literal.startsWith("\"") && literal.endsWith("\"")
                ? literal.substring(1, literal.length() - 1) : literal;
    }

    // -------------------------------------------------------------------------------------------
    // Operators
    // -------------------------------------------------------------------------------------------

    private Part operator(Operator operator) {
        String symbol = operator.getOperatorName();

        if (operator.getChildCount() == 1) {
            Part operand = translate(operator.getChild(0));
            if ((operator instanceof OpMinus || operator instanceof OpPlus) && operand.literal) {
                return Part.literal(symbol + operand.text());
            }
            if (operator instanceof OpMinus) return new Part().add(Token.op("negative")).add(operand);
            if (operator instanceof OpPlus) return operand;
            return raw(operator);
        }

        String phrase = Phrases.operator(symbol);
        if (phrase == null || operator.getChildCount() != 2) return raw(operator);

        Part left = translate(operator.getChild(0));
        Part right = translate(operator.getChild(1));
        boolean logical = operator instanceof OpAnd || operator instanceof OpOr;
        Part part = new Part();

        if (("==".equals(symbol) || "!=".equals(symbol)) && right.isNullLiteral()) {
            return part.add(left).add(Token.op("==".equals(symbol) ? "is absent" : "is present"));
        }

        addOperand(part, left, logical ? phrase : null);
        part.add(Token.op(phrase));
        addOperand(part, right, logical ? phrase : null);
        if (logical) part.logicalOperator = phrase;
        return part;
    }

    /** Mixed and/or keeps its grouping visible with parentheses. */
    private static void addOperand(Part into, Part operand, String parentLogical) {
        if (parentLogical != null && operand.logicalOperator != null && !parentLogical.equals(operand.logicalOperator)) {
            into.add(Token.op("(")).add(operand).add(Token.op(")"));
        } else {
            into.add(operand);
        }
    }

    /**
     * "not X": when X reads "... is ..." the negation moves inside ("items is not empty"),
     * otherwise it is prefixed.
     */
    private static Part not(Part operand) {
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

    private Part between(OperatorBetween between) {
        Part left = translate(between.getChild(0));
        SpelNode right = between.getChild(1);
        if (!(right instanceof InlineList) || right.getChildCount() != 2) return raw(between);
        return new Part().add(left).add(Token.op("is between")).add(translate(right.getChild(0)))
                .add(Token.op("and")).add(translate(right.getChild(1)));
    }

    private Part assign(Assign assign) {
        Part target = translate(assign.getChild(0));
        Part value = translate(assign.getChild(1));
        if (target.path == null || target.path.isEmpty()) return raw(assign);

        Part part = new Part().add(Token.op("set")).add(Token.binding(target.path, Phrases.humanize(target.path)))
                .add(Token.op("to")).add(value);
        part.writes.add(String.join(".", target.path));
        return part;
    }

    private Part ternary(Ternary ternary) {
        return new Part().add(Token.keyword("if")).add(translate(ternary.getChild(0)))
                .add(Token.keyword("then")).add(translate(ternary.getChild(1)))
                .add(Token.keyword("otherwise")).add(translate(ternary.getChild(2)));
    }

    private Part elvis(Elvis elvis) {
        return new Part().add(translate(elvis.getChild(0))).add(Token.op("or"))
                .add(translate(elvis.getChild(1))).add(Token.keyword("when absent"));
    }

    private Part inlineList(InlineList list) {
        List<Part> items = new ArrayList<>();
        for (int i = 0; i < list.getChildCount(); i++) items.add(translate(list.getChild(i)));

        if (items.stream().allMatch(item -> item.literal)) {
            return Part.literal(items.stream().map(Part::text).reduce((a, b) -> a + ", " + b).orElse(""));
        }

        Part part = new Part().add(Token.keyword("the list"));
        appendArguments(part, items);
        return part;
    }

    // -------------------------------------------------------------------------------------------
    // Fallback
    // -------------------------------------------------------------------------------------------

    /** The node as written, placeholders restored; marks the translation incomplete. */
    private Part raw(SpelNode node) {
        int start = Math.max(0, node.getStartPosition());
        // A bean reference's position starts after its '@'.
        if (start > 0 && text.charAt(start - 1) == '@') start--;
        int end = Math.min(text.length(), Math.max(node.getEndPosition(), endOf(node)));
        end = closeBrackets(start, end);
        String slice = end > start ? text.substring(start, end) : node.toStringAST();
        return Part.raw(placeholders.restore(slice));
    }

    /** A node's end position covers only its own token; the subtree ends where its last descendant does. */
    private static int endOf(SpelNode node) {
        int end = node.getEndPosition();
        for (int i = 0; i < node.getChildCount(); i++) end = Math.max(end, endOf(node.getChild(i)));
        return end;
    }

    /** Extends a slice over the closing brackets of calls, indexes and lists it opened. */
    private int closeBrackets(int start, int end) {
        int open = 0;
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (c == '(' || c == '[' || c == '{') open++;
            else if (c == ')' || c == ']' || c == '}') open--;
        }
        while (open > 0 && end < text.length()) {
            char c = text.charAt(end);
            if (c == ')' || c == ']' || c == '}') open--;
            else if (!Character.isWhitespace(c)) break;
            end++;
        }
        // A constructor's or call's argument list that no child node covered: "new Foo()".
        if (end < text.length() && (text.charAt(end) == '(' || text.charAt(end) == '[')) {
            int depth = 0;
            for (int i = end; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '(' || c == '[' || c == '{') depth++;
                else if (c == ')' || c == ']' || c == '}') depth--;
                if (depth == 0) return i + 1;
            }
        }
        return end;
    }
}
