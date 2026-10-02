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

import org.rulii.explorer.descriptor.Token;
import org.rulii.explorer.expression.ExpressionAnalysis;
import org.rulii.explorer.expression.plain.Part;
import org.rulii.explorer.expression.plain.Phrases;
import org.rulii.explorer.expression.plain.Placeholders;
import org.rulii.explorer.expression.script.ScriptNode.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Walks a parsed JavaScript or Java script and renders it in plain English through the same
 * phrase book as SpEL, collecting the bindings it reads and writes. The bindings are the
 * {@code ctx} object, so {@code ctx.order.total} and {@code ctx.order.getTotal()} are both the
 * binding read "order total"; a bare name is a local variable or a global, never a binding.
 * Anything the phrase book cannot express becomes a {@code raw} token holding its source text
 * (FR-17); a call it cannot phrase keeps its arguments translated.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
final class ScriptTranslator {

    private static final String BINDINGS = "ctx";
    /** Java number unwrapping and conversions that change the type, not the meaning. */
    private static final Set<String> TRANSPARENT = Set.of("doubleValue", "intValue", "longValue", "floatValue", "shortValue", "byteValue", "valueOf", "booleanValue");
    /** {@code Math} functions read after their argument: "order total rounded down". */
    private static final Map<String, String> MATH_SUFFIX = Map.of("floor", "rounded down", "ceil", "rounded up", "round", "rounded", "trunc", "truncated");
    /** {@code Math} functions read before their argument. */
    private static final Map<String, String> MATH_PREFIX = Map.of("abs", "absolute value of", "sqrt", "square root of");
    private static final Set<String> NUMBER_CONVERSIONS = Set.of("Number", "parseInt", "parseFloat");

    private final String text;
    private final Placeholders placeholders;
    private final Set<String> locals = new HashSet<>();

    ScriptTranslator(Placeholders placeholders) {
        super();
        this.placeholders = placeholders;
        this.text = placeholders.rewritten();
    }

    /** Parses and translates a script; the shared body of the JavaScript and Java analyzers. */
    static ExpressionAnalysis analyze(String sourceText, Dialect dialect) {
        if (sourceText == null || sourceText.isBlank()) return ExpressionAnalysis.unparsed();
        Placeholders placeholders = Placeholders.of(sourceText.strip(), Placeholders.PREFIX);
        Program program;
        try {
            program = ScriptParser.parse(placeholders.rewritten(), dialect);
        } catch (ScriptParser.SyntaxError e) {
            return ExpressionAnalysis.unparsed();
        }
        Part part = new ScriptTranslator(placeholders).translate(program);
        boolean complete = part.tokens.stream().noneMatch(token -> Token.RAW.equals(token.t()));
        return new ExpressionAnalysis(complete, new ArrayList<>(part.tokens), new ArrayList<>(part.reads), new ArrayList<>(part.writes));
    }

    Part translate(Program program) {
        return sequence(program.statements());
    }

    // ── Statements ──────────────────────────────────────────────────────────

    /** Statements read one after another, joined by "then". */
    private Part sequence(List<ScriptNode> statements) {
        Part part = new Part();
        for (int i = 0; i < statements.size(); i++) {
            Part statement = statement(statements.get(i), i == statements.size() - 1);
            if (statement.tokens.isEmpty()) { part.absorb(statement); continue; }
            if (!part.tokens.isEmpty()) part.add(Token.keyword("then"));
            part.add(statement);
        }
        return part;
    }

    private Part statement(ScriptNode node, boolean last) {
        if (node instanceof VarDecl declaration) {
            locals.add(declaration.name());
            Part part = new Part().add(Token.keyword("let")).add(Token.literal(declaration.name()));
            if (declaration.init() != null) part.add(Token.keyword("be")).add(expression(declaration.init()));
            return part;
        }
        if (node instanceof Return ret) {
            if (ret.value() == null) return Part.of(Token.keyword("stop"));
            Part value = expression(ret.value());
            return last ? value : new Part().add(Token.keyword("return")).add(value);
        }
        if (node instanceof If branch) {
            Part part = new Part().add(Token.keyword("if")).add(expression(branch.test())).add(Token.keyword("then")).add(sequence(branch.then()));
            if (branch.otherwise() != null) part.add(Token.keyword("otherwise")).add(sequence(branch.otherwise()));
            return part;
        }
        return expression(node);
    }

    // ── Expressions ─────────────────────────────────────────────────────────

    private Part expression(ScriptNode node) {
        if (node instanceof Ident ident) return ident(ident);
        if (node instanceof Num number) return Part.literal(number.text());
        if (node instanceof Str string) return Part.literal("\"" + placeholders.restore(string.value()) + "\"");
        if (node instanceof Bool bool) return Part.literal(String.valueOf(bool.value()));
        if (node instanceof Nothing) return Part.literal("nothing");
        if (node instanceof Template template) return template(template);
        if (node instanceof ArrayLit array) return array(array);
        if (node instanceof Member member) return member(member);
        if (node instanceof Index index) return index(index);
        if (node instanceof Call call) return call(call);
        if (node instanceof Cast cast) return expression(cast.operand());
        if (node instanceof Unary unary) return unary(unary);
        if (node instanceof Update update) return update(update);
        if (node instanceof Binary binary) return binary(binary);
        if (node instanceof Logical logical) return logical(logical);
        if (node instanceof Assign assign) return assign(assign);
        if (node instanceof Cond cond) return cond(cond);
        return raw(node);
    }

    private Part ident(Ident ident) {
        String name = ident.name();
        Placeholders.Placeholder placeholder = placeholders.forVariable(name);
        if (placeholder != null) return Part.of(Token.placeholder(placeholder.key(), placeholder.defaultValue()));
        if (BINDINGS.equals(name)) return Part.binding(List.of());
        if (locals.contains(name)) return Part.of(Token.literal(name));
        return raw(ident);
    }

    private Part template(Template template) {
        String body = template.text().substring(1, template.text().length() - 1);
        if (body.contains("${") || body.contains(Placeholders.PREFIX)) return raw(template);
        return Part.literal("\"" + body + "\"");
    }

    private Part array(ArrayLit array) {
        List<Part> items = new ArrayList<>();
        for (ScriptNode item : array.items()) items.add(expression(item));
        if (items.stream().allMatch(item -> item.literal)) {
            return Part.literal(items.stream().map(Part::text).reduce((a, b) -> a + ", " + b).orElse(""));
        }
        Part part = new Part().add(Token.keyword("the list"));
        Part.appendArguments(part, items);
        return part;
    }

    /** The binding path of {@code ctx.a.b}, the empty path for {@code ctx} itself, or null when the node is not rooted at the bindings. */
    private List<String> pathOf(ScriptNode node) {
        if (node instanceof Ident ident) return BINDINGS.equals(ident.name()) && !locals.contains(BINDINGS) ? new ArrayList<>() : null;
        if (node instanceof Member member) {
            List<String> path = pathOf(member.object());
            if (path == null) return null;
            path.add(member.name());
            return path;
        }
        return null;
    }

    /** The identifier a member chain or call hangs off, such as {@code java} in {@code java.time.LocalDate.now()}; null otherwise. */
    private static Ident rootOf(ScriptNode node) {
        if (node instanceof Ident ident) return ident;
        if (node instanceof Member member) return rootOf(member.object());
        if (node instanceof Call call) return rootOf(call.callee());
        return null;
    }

    private boolean isKnown(Ident ident) {
        return BINDINGS.equals(ident.name()) || locals.contains(ident.name()) || placeholders.forVariable(ident.name()) != null;
    }

    private Part member(Member member) {
        List<String> path = pathOf(member);
        if (path != null) {
            if (path.size() > 1 && "length".equals(path.get(path.size() - 1))) {
                return new Part().add(Token.call("length of")).add(Part.binding(path.subList(0, path.size() - 1)));
            }
            return Part.binding(path);
        }
        Ident root = rootOf(member);
        if (root != null && !isKnown(root)) return raw(member);
        // A property of a call or index result: "price of items at 0"
        return new Part().add(Token.call(Phrases.humanize(member.name()) + " of")).add(expression(member.object()));
    }

    private Part index(Index index) {
        Part target = target(index.object());
        if (target == null) return raw(index);
        return new Part().add(target).add(Token.op("at")).add(expression(index.index()));
    }

    /**
     * A binding path or any other receiver rooted at the bindings or a local; null when the
     * receiver hangs off an unknown name and has to be shown raw. A receiver that is only partly
     * raw still translates around the raw part, so its reads survive.
     */
    private Part target(ScriptNode node) {
        List<String> path = pathOf(node);
        if (path != null) return Part.binding(path);
        Ident root = rootOf(node);
        if (root != null && !isKnown(root)) return null;
        return expression(node);
    }

    private Part call(Call call) {
        List<Part> args = new ArrayList<>();
        for (ScriptNode arg : call.args()) args.add(expression(arg));

        if (call.callee() instanceof Ident function) {
            if (NUMBER_CONVERSIONS.contains(function.name()) && args.size() >= 1) return new Part().add(args.get(0)).add(Token.call("as a number"));
            if ("String".equals(function.name()) && args.size() == 1) return new Part().add(args.get(0)).add(Token.call("as text"));
            return unknownCall(call, args);
        }
        if (!(call.callee() instanceof Member member)) return raw(call);

        String name = member.name();
        if (member.object() instanceof Ident object && "Math".equals(object.name())) return math(call, name, args);
        Ident root = rootOf(member.object());
        if (root != null && !isKnown(root)) return unknownCall(call, args);

        List<String> path = pathOf(member.object());
        if (path != null && path.isEmpty()) return bindingsApi(call, name, args);

        Part target = target(member.object());
        if (target == null) return unknownCall(call, args);

        if (TRANSPARENT.contains(name) && args.isEmpty()) return target;
        if ("toString".equals(name) && args.isEmpty()) return new Part().add(target).add(Token.call("as text"));
        // items.get(0) is an index: "items at 0"
        if ("get".equals(name) && args.size() == 1) return new Part().add(target).add(Token.op("at")).add(args.get(0));

        boolean onBinding = target.path != null && !target.path.isEmpty();
        // A getter is the property: ctx.order.getTotal() is order total
        if (onBinding && name.startsWith("get") && name.length() > 3 && args.isEmpty()) {
            List<String> property = new ArrayList<>(target.path);
            property.add(decapitalise(name.substring(3)));
            return Part.binding(property);
        }
        // A setter is a write: ctx.customer.setTier('GOLD') sets customer tier
        if (onBinding && name.startsWith("set") && name.length() > 3 && args.size() == 1) {
            List<String> written = new ArrayList<>(target.path);
            written.add(decapitalise(name.substring(3)));
            Part part = new Part().add(Token.op("set")).add(Token.binding(written, Phrases.humanize(written))).add(Token.op("to")).add(args.get(0));
            part.writes.add(String.join(".", written));
            return part;
        }

        String prefix = Phrases.prefixMethod(name);
        String infix = Phrases.infixMethod(name);
        Part part = new Part();
        if (prefix != null && args.isEmpty()) {
            part.add(Token.call(prefix)).add(target);
        } else if (infix != null) {
            part.add(target).add(Token.call(infix));
            Part.appendArguments(part, args);
        } else {
            Phrases.GenericMethod generic = Phrases.genericMethod(name);
            if (generic.prefix() && args.isEmpty()) {
                part.add(Token.call(generic.phrase())).add(target);
            } else {
                part.add(target).add(Token.call(generic.phrase()));
                Part.appendArguments(part, args);
            }
        }
        return part;
    }

    /** A call the phrase book has nothing for: the callee as written, the arguments translated, so their reads survive. */
    private Part unknownCall(Call call, List<Part> args) {
        int end = Math.min(call.callee().end(), text.length());
        Part part = Part.raw(placeholders.restore(text.substring(Math.min(call.callee().start(), end), end)));
        part.add(Token.op("("));
        Part.appendArguments(part, args);
        return part.add(Token.op(")"));
    }

    /** ctx.setValue('x', v) / ctx.bind('x', v) / ctx.getValue('x'): the bindings' own API. */
    private Part bindingsApi(Call call, String name, List<Part> args) {
        if (("setValue".equals(name) || "bind".equals(name) || "setValueOrBind".equals(name)) && args.size() == 2 && args.get(0).literal) {
            List<String> written = List.of(unquote(args.get(0).text()));
            Part part = new Part().add(Token.op("set")).add(Token.binding(written, Phrases.humanize(written))).add(Token.op("to")).add(args.get(1));
            part.writes.add(String.join(".", written));
            return part;
        }
        if ("getValue".equals(name) && args.size() == 1 && args.get(0).literal) return Part.binding(List.of(unquote(args.get(0).text())));
        return raw(call);
    }

    private Part math(Call call, String name, List<Part> args) {
        if (MATH_SUFFIX.containsKey(name) && args.size() == 1) return new Part().add(args.get(0)).add(Token.call(MATH_SUFFIX.get(name)));
        if (MATH_PREFIX.containsKey(name) && args.size() == 1) return new Part().add(Token.call(MATH_PREFIX.get(name))).add(args.get(0));
        if (("max".equals(name) || "min".equals(name)) && args.size() == 2) {
            return new Part().add(Token.call("max".equals(name) ? "the larger of" : "the smaller of")).add(args.get(0)).add(Token.op("and")).add(args.get(1));
        }
        if ("pow".equals(name) && args.size() == 2) return new Part().add(args.get(0)).add(Token.op("to the power of")).add(args.get(1));
        return unknownCall(call, args);
    }

    private Part unary(Unary unary) {
        switch (unary.op()) {
            case "!": return Part.negate(expression(unary.operand()));
            case "-": {
                Part operand = expression(unary.operand());
                if (operand.literal) return Part.literal("-" + operand.text());
                return new Part().add(Token.op("negative")).add(operand);
            }
            case "+": return expression(unary.operand());
            default: return raw(unary);
        }
    }

    private Part update(Update update) {
        List<String> path = pathOf(update.target());
        if (path == null || path.isEmpty()) return raw(update);
        Part part = new Part().add(Token.op("++".equals(update.op()) ? "add" : "subtract")).add(Token.literal("1"))
                .add(Token.op("++".equals(update.op()) ? "to" : "from")).add(Token.binding(path, Phrases.humanize(path)));
        part.reads.add(String.join(".", path));
        part.writes.add(String.join(".", path));
        return part;
    }

    private Part binary(Binary binary) {
        String op = binary.op();
        String symbol = switch (op) {
            case "===" -> "==";
            case "!==" -> "!=";
            case "**" -> "^";
            default -> op;
        };
        String phrase = Phrases.operator(symbol);
        if (phrase == null || "instanceof".equals(op) || "in".equals(op)) return raw(binary);

        Part left = expression(binary.left());
        Part right = expression(binary.right());
        if (("==".equals(symbol) || "!=".equals(symbol)) && right.isNullLiteral()) {
            return new Part().add(left).add(Token.op("==".equals(symbol) ? "is absent" : "is present"));
        }
        return new Part().add(left).add(Token.op(phrase)).add(right);
    }

    private Part logical(Logical logical) {
        Part left = expression(logical.left());
        Part right = expression(logical.right());
        if ("??".equals(logical.op())) {
            return new Part().add(left).add(Token.op("or")).add(right).add(Token.keyword("when absent"));
        }
        String phrase = "&&".equals(logical.op()) ? "and" : "or";
        Part part = new Part();
        Part.addOperand(part, left, phrase);
        part.add(Token.op(phrase));
        Part.addOperand(part, right, phrase);
        part.logicalOperator = phrase;
        return part;
    }

    private Part assign(Assign assign) {
        Part value = expression(assign.value());
        List<String> path = pathOf(assign.target());
        if (path != null && !path.isEmpty()) {
            Token binding = Token.binding(path, Phrases.humanize(path));
            String joined = String.join(".", path);
            Part part = new Part();
            switch (assign.op()) {
                case "=" -> part.add(Token.op("set")).add(binding).add(Token.op("to")).add(value);
                case "+=" -> part.add(Token.op("add")).add(value).add(Token.op("to")).add(binding);
                case "-=" -> part.add(Token.op("subtract")).add(value).add(Token.op("from")).add(binding);
                case "*=" -> part.add(Token.op("multiply")).add(binding).add(Token.op("by")).add(value);
                case "/=" -> part.add(Token.op("divide")).add(binding).add(Token.op("by")).add(value);
                default -> { return raw(assign); }
            }
            if (!"=".equals(assign.op())) part.reads.add(joined);
            part.writes.add(joined);
            return part;
        }
        if (assign.target() instanceof Ident local && locals.contains(local.name()) && "=".equals(assign.op())) {
            return new Part().add(Token.op("set")).add(Token.literal(local.name())).add(Token.op("to")).add(value);
        }
        return raw(assign);
    }

    private Part cond(Cond cond) {
        return new Part().add(Token.keyword("if")).add(expression(cond.test()))
                .add(Token.keyword("then")).add(expression(cond.then()))
                .add(Token.keyword("otherwise")).add(expression(cond.otherwise()));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** The node as written, placeholders restored; marks the translation incomplete. */
    private Part raw(ScriptNode node) {
        int start = Math.max(0, Math.min(node.start(), text.length()));
        int end = Math.max(start, Math.min(node.end(), text.length()));
        return Part.raw(placeholders.restore(text.substring(start, end)));
    }

    private static String unquote(String literal) {
        return literal.length() >= 2 && literal.startsWith("\"") && literal.endsWith("\"") ? literal.substring(1, literal.length() - 1) : literal;
    }

    private static String decapitalise(String name) {
        return name.isEmpty() ? name : name.substring(0, 1).toLowerCase(Locale.ROOT) + name.substring(1);
    }
}
