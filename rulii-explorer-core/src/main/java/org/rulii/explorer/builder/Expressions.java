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
package org.rulii.explorer.builder;

import org.rulii.bind.Bindings;
import org.rulii.context.RuleContext;
import org.rulii.explorer.descriptor.Analysis;
import org.rulii.explorer.descriptor.Expression;
import org.rulii.explorer.descriptor.ExpressionKind;
import org.rulii.explorer.descriptor.Parameter;
import org.rulii.explorer.descriptor.Placeholder;
import org.rulii.explorer.expression.ExpressionAnalysis;
import org.rulii.explorer.expression.ExpressionAnalyzers;
import org.rulii.model.ExpressionInfo;
import org.rulii.model.MethodDefinition;
import org.rulii.model.ParameterDefinition;
import org.rulii.model.Runnable;
import org.rulii.model.action.ChainedAction;
import org.rulii.model.action.Action;
import org.rulii.model.condition.Condition;
import org.rulii.model.function.Function;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Maps rulii {@link ExpressionInfo} to the descriptor's {@link Expression}: scripts get
 * analysed, compiled code gets a signature (FR-15), composites keep their parts. Hooks rulii
 * adds itself (default rule set handlers, the validating check) are dropped.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 *
 */
final class Expressions {

    private final ExpressionAnalyzers analyzers;
    private final PlaceholderFilter placeholderValues;

    /**
     * @param analyzers         the script analyzers.
     * @param placeholderValues which placeholder values to show; null shows none (the default).
     */
    Expressions(ExpressionAnalyzers analyzers, PlaceholderFilter placeholderValues) {
        super();
        this.analyzers = analyzers;
        this.placeholderValues = placeholderValues;
    }

    /** The expression of a condition; null when there is none. */
    Expression of(Condition condition) {
        return condition == null ? null : mapCondition(condition.getExpression());
    }

    /** The expression of an action; null when there is none. */
    Expression of(Action action) {
        return action == null ? null : mapAction(action.getExpression());
    }

    /** The expression of a function; null when there is none. */
    Expression of(Function<?> function) {
        return function == null ? null : mapFunction(function.getExpression());
    }

    /** What an expression is used as; decides the name compiled lambdas are shown with. */
    enum Role { CONDITION, ACTION, FUNCTION, UNKNOWN }

    Expression mapCondition(ExpressionInfo info) {
        return map(info, Role.CONDITION);
    }

    Expression mapAction(ExpressionInfo info) {
        return map(info, Role.ACTION);
    }

    Expression mapFunction(ExpressionInfo info) {
        return map(info, Role.FUNCTION);
    }

    /**
     * A rule set or flow hook with rulii's own plumbing removed: the default result extractor
     * and error handler of a rule set, and the violation check a validating rule set appends
     * to its finalizer. Null when nothing the application wrote remains.
     */
    Expression hook(Condition condition) {
        return condition == null || isRuliiInternal(condition, condition.getExpression()) ? null : of(condition);
    }

    Expression hook(Function<?> function) {
        return function == null || isRuliiInternal(function, function.getExpression()) ? null : of(function);
    }

    Expression hook(Action action) {
        if (action == null || isRuliiInternal(action, action.getExpression())) return null;

        if (action instanceof ChainedAction chained) {
            Expression main = hook(chained.getMainAction());
            Expression other = hook(chained.getChainedAction());
            if (main == null) return other;
            if (other == null) return main;
            ExpressionInfo info = chained.getExpression();
            List<Expression> operands = "andThen".equals(info.operator()) ? List.of(main, other) : List.of(other, main);
            return composite(info.operator(), operands);
        }

        return of(action);
    }

    /**
     * Maps an expression as is.
     *
     * @param info the rulii expression; may be null.
     * @return the expression, or null.
     */
    Expression map(ExpressionInfo info) {
        return map(info, Role.UNKNOWN);
    }

    private Expression map(ExpressionInfo info, Role role) {
        if (info == null) return null;

        return switch (info.kind()) {
            case SCRIPT -> script(info);
            case COMPILED -> compiled(info.method(), role);
            case COMPOSITE -> composite(info.operator(), info.operands().stream().map(o -> map(o, role)).toList());
        };
    }

    private Expression script(ExpressionInfo info) {
        Optional<ExpressionAnalysis> analysis = analyzers.analyze(info.language(), info.sourceText());
        Analysis plain = analysis.map(a -> new Analysis(a.complete(), a.tokens())).orElse(null);
        List<String> reads = analysis.map(ExpressionAnalysis::reads).orElse(List.of());
        List<String> writes = analysis.map(ExpressionAnalysis::writes).orElse(List.of());
        return new Expression(ExpressionKind.SCRIPT, info.language(), info.sourceText(), placeholders(info), plain,
                reads, writes, null, null, null);
    }

    /**
     * The placeholders of a script in source order: as written, or with the values the script
     * compiled with when the builder asked for them and the filter lets each key through. The
     * resolved text itself never reaches the descriptor: only the per-placeholder values that
     * passed the filter do.
     */
    private List<Placeholder> placeholders(ExpressionInfo info) {
        List<Placeholder> found = placeholderValues == null
                ? PlaceholderValues.asWritten(info.sourceText())
                : PlaceholderValues.resolve(info.sourceText(), info.resolvedText());
        if (found.isEmpty()) return null;
        if (placeholderValues == null) return found;
        return found.stream()
                .map(p -> p.value() != null && !placeholderValues.show(p.key(), p.value()) ? p.asHidden() : p)
                .toList();
    }

    private Expression compiled(MethodDefinition method, Role role) {
        List<String> reads = method == null ? List.of() : parameterReads(method);
        return new Expression(ExpressionKind.COMPILED, null, null, null, null, reads, List.of(),
                method == null ? null : signature(method, role), null, null);
    }

    private Expression composite(String operator, List<Expression> operands) {
        List<String> reads = operands.stream().flatMap(e -> e.reads().stream()).collect(Collectors.toCollection(TreeSet::new))
                .stream().toList();
        List<String> writes = operands.stream().flatMap(e -> e.writes().stream()).collect(Collectors.toCollection(TreeSet::new))
                .stream().toList();
        return new Expression(ExpressionKind.COMPOSITE, null, null, null, null, reads, writes, null, operator, operands);
    }

    /**
     * Whether a compiled runnable is one rulii created itself. Lambda classes are nested under
     * the class that declares them, so the target class of a default rule set hook is
     * {@code org.rulii.ruleset.RuleSetBuilder$$Lambda...}.
     */
    static boolean isRuliiInternal(Runnable<?> runnable, ExpressionInfo info) {
        if (info == null || info.kind() == ExpressionInfo.Kind.SCRIPT) return false;
        Object target = runnable.getTarget();
        if (target == null) return false;
        String targetClass = target.getClass().getName();
        return Rulii.isInternalClass(targetClass);
    }

    /** Whether the expression, or any part of it, is compiled code whose writes are unknown. */
    static boolean hasCompiledPart(Expression expression) {
        if (expression == null) return false;
        if (expression.kind() == ExpressionKind.COMPILED) return true;
        return expression.operands() != null && expression.operands().stream().anyMatch(Expressions::hasCompiledPart);
    }

    /**
     * A readable signature: {@code boolean test(Order order, Customer customer)}. Lambdas get
     * the name of the functional method their return type implies.
     */
    static String signature(MethodDefinition method, Role role) {
        Type returnType = method.getReturnType();
        String name = method.getName();
        String returns = simpleName(returnType);

        // A lambda's method is rulii's functional interface method (or a synthetic lambda$ method):
        // show the role instead, as a reader would write it.
        boolean lambda = name == null || name.contains("lambda$")
                || (method.getMethod() != null && Rulii.isInternalClass(method.getMethod().getDeclaringClass()));
        if (lambda) {
            switch (role) {
                case CONDITION -> { name = "test"; returns = "boolean"; }
                case ACTION -> { name = "run"; returns = "void"; }
                case FUNCTION -> name = "apply";
                default -> name = functionalName(returnType);
            }
        }

        String params = method.getParameterDefinitions().stream()
                .map(p -> simpleName(p.getType()) + " " + p.getName())
                .collect(Collectors.joining(", "));
        return returns + " " + name + "(" + params + ")";
    }

    private static String functionalName(Type returnType) {
        if (returnType == boolean.class || returnType == Boolean.class) return "test";
        if (returnType == void.class || returnType == Void.class) return "run";
        return "apply";
    }

    /** Declared parameters of a compiled method, minus the rule context rulii passes itself. */
    static List<String> parameterReads(MethodDefinition method) {
        return parameters(method).stream().map(Parameter::name).toList();
    }

    /**
     * The declared parameters of the given methods, in order, without duplicates and without
     * the rule context or bindings parameters rulii passes itself.
     */
    static List<Parameter> parameters(MethodDefinition... methods) {
        Map<String, Parameter> byName = new LinkedHashMap<>();
        for (MethodDefinition method : methods) {
            if (method == null) continue;
            for (ParameterDefinition p : method.getParameterDefinitions()) {
                if (isContext(p.getType())) continue;
                byName.putIfAbsent(p.getName(), new Parameter(p.getName(), typeName(p.getType()),
                        !p.isOptionalType() && !p.hasDefaultValue(), p.getDefaultValueText(),
                        p.getMatchUsing() != null ? p.getMatchUsing().getSimpleName() : null,
                        blankToNull(p.getDescription())));
            }
        }
        return new ArrayList<>(byName.values());
    }

    private static boolean isContext(Type type) {
        return type instanceof Class<?> c && (RuleContext.class.isAssignableFrom(c) || Bindings.class.isAssignableFrom(c));
    }

    static String typeName(Type type) {
        if (type instanceof Class<?> c) return c.getName();
        return type == null ? null : type.getTypeName();
    }

    static String simpleName(Type type) {
        if (type instanceof Class<?> c) return c.getSimpleName();
        if (type instanceof ParameterizedType p) {
            String args = java.util.Arrays.stream(p.getActualTypeArguments()).map(Expressions::simpleName).collect(Collectors.joining(", "));
            return simpleName(p.getRawType()) + "<" + args + ">";
        }
        return type == null ? "?" : type.getTypeName();
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
