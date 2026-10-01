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

import org.rulii.explorer.Explorer;
import org.rulii.explorer.descriptor.ApplicationInfo;
import org.rulii.explorer.descriptor.Artifact;
import org.rulii.explorer.descriptor.ArtifactKind;
import org.rulii.explorer.descriptor.ArtifactType;
import org.rulii.explorer.descriptor.BindingUsage;
import org.rulii.explorer.descriptor.Command;
import org.rulii.explorer.descriptor.Commands;
import org.rulii.explorer.descriptor.Descriptor;
import org.rulii.explorer.descriptor.Expression;
import org.rulii.explorer.descriptor.PackageInfo;
import org.rulii.explorer.descriptor.Parameter;
import org.rulii.explorer.descriptor.Problem;
import org.rulii.explorer.descriptor.ProblemSeverity;
import org.rulii.explorer.descriptor.Reference;
import org.rulii.explorer.descriptor.RuleDetails;
import org.rulii.explorer.descriptor.RuleFlowDetails;
import org.rulii.explorer.descriptor.RuleSetDetails;
import org.rulii.explorer.descriptor.Source;
import org.rulii.explorer.descriptor.ValidationDetails;
import org.rulii.explorer.descriptor.ValueSource;
import org.rulii.explorer.expression.ExpressionAnalyzers;
import org.rulii.explorer.problem.ProblemCheck;
import org.rulii.explorer.problem.ProblemCodes;
import org.rulii.explorer.problem.ProblemContext;
import org.rulii.model.InputParameter;
import org.rulii.model.MethodDefinition;
import org.rulii.model.Runnable;
import org.rulii.model.SourceDefinition;
import org.rulii.model.action.Action;
import org.rulii.model.condition.Condition;
import org.rulii.model.function.Function;
import org.rulii.registry.RuleRegistry;
import org.rulii.rule.Rule;
import org.rulii.rule.RuleDefinition;
import org.rulii.ruleflow.RuleFlow;
import org.rulii.ruleflow.RuleFlowDefinition;
import org.rulii.ruleset.RuleSet;
import org.rulii.ruleset.RuleSetDefinition;
import org.rulii.validation.SuppliedValidationRule;
import org.rulii.validation.ValidationRule;
import org.rulii.validation.ValueValidationRule;
import org.rulii.validation.ValueValidationRuleBuilder;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Walks a {@link RuleRegistry} once and produces the {@link Descriptor}. Registered artifacts
 * are keyed by their registry name; inline members and targets that are not registered get
 * path ids and {@code registered = false}. Anything that throws while being described is kept
 * with what could be read and reported as {@code UNDESCRIBABLE} (NFR-22).
 *
 * @author Max Arulananthan
 * @since 1.0
 */
final class Describer {

    private static final String PREDEFINED_PACKAGE = "org.rulii.validation.rules.";
    private static final String RULE_VIOLATIONS = "ruleViolations";

    private final RuleRegistry registry;
    private final Expressions expressions;
    private final boolean includeSources;
    private final String applicationName;
    private final String ruliiVersion;
    private final List<ProblemCheck> checks;

    private final Map<Object, String> ids = new IdentityHashMap<>();
    private final Map<String, Artifact> artifacts = new LinkedHashMap<>();
    private final List<Reference> references = new ArrayList<>();
    private final List<Problem> problems = new ArrayList<>();

    Describer(RuleRegistry registry, ExpressionAnalyzers analyzers, boolean includeSources, String applicationName,
              String ruliiVersion, List<ProblemCheck> checks) {
        super();
        this.registry = registry;
        this.expressions = new Expressions(analyzers);
        this.includeSources = includeSources;
        this.applicationName = applicationName;
        this.ruliiVersion = ruliiVersion;
        this.checks = checks;
    }

    Descriptor describe() {
        List<String> names = new ArrayList<>(registry.getNames());
        names.sort(Comparator.naturalOrder());

        Map<String, Runnable<?>> registered = new LinkedHashMap<>();
        for (String name : names) {
            try {
                Runnable<?> runnable = registry.get(name);
                if (runnable != null) {
                    ids.put(runnable, name);
                    registered.put(name, runnable);
                }
            } catch (RuntimeException e) {
                undescribable(name, e);
            }
        }

        registered.forEach((name, runnable) -> describe(name, runnable, true));

        List<Artifact> sortedArtifacts = new ArrayList<>(artifacts.values());
        sortedArtifacts.sort(Comparator.comparing(Artifact::type).thenComparing(Artifact::id, Rulii::compareNatural));

        List<Reference> sortedReferences = new ArrayList<>(references);
        sortedReferences.sort(Comparator.comparing(Reference::from, Rulii::compareNatural).thenComparing(Reference::path, Rulii::compareNatural).thenComparing(Reference::to, Rulii::compareNatural));

        for (ProblemCheck check : checks) problems.addAll(check.check(new ProblemContext(sortedArtifacts, sortedReferences)));
        problems.sort(Comparator.comparing(Problem::severity)
                .thenComparing(Problem::artifact, Comparator.nullsFirst(Rulii::compareNatural))
                .thenComparing(Problem::path, Comparator.nullsFirst(Rulii::compareNatural))
                .thenComparing(Problem::code).thenComparing(Problem::message));

        return new Descriptor(Explorer.DESCRIPTOR_VERSION,
                new ApplicationInfo(applicationName, ruliiVersion, Explorer.version()),
                packages(sortedArtifacts), sortedArtifacts, sortedReferences, bindings(sortedArtifacts), problems);
    }

    // -------------------------------------------------------------------------------------------
    // Artifacts
    // -------------------------------------------------------------------------------------------

    private void describe(String id, Runnable<?> runnable, boolean registered) {
        if (artifacts.containsKey(id)) return;

        try {
            if (runnable instanceof Rule rule) artifacts.put(id, rule(id, rule, registered));
            else if (runnable instanceof RuleSet<?> ruleSet) artifacts.put(id, ruleSet(id, ruleSet, registered));
            else if (runnable instanceof RuleFlow<?> ruleFlow) artifacts.put(id, ruleFlow(id, ruleFlow, registered));
        } catch (RuntimeException e) {
            artifacts.put(id, new Artifact(id, safeName(runnable, id), typeOf(runnable), ArtifactKind.UNKNOWN, registered,
                    null, null, null, null, List.of(), null, null, null, null));
            undescribable(id, e);
        }
    }

    private Artifact rule(String id, Rule rule, boolean registered) {
        RuleDefinition def = rule.getDefinition();
        Object target = rule.getTarget();
        SourceDefinition sourceDefinition = def.getSource();
        Class<?> ruleClass = def.getRuleClass();
        ArtifactKind kind = ruleKind(def, target, sourceDefinition);

        // A supplied validation rule wraps the condition the application wrote; show that, not
        // the wrapper's isValid(RuleContext). Its otherwise action is rulii's violation recorder.
        // A predefined validator's rule methods are all rulii's own: the validation section
        // describes it, so the rule section stays empty.
        boolean supplied = target instanceof SuppliedValidationRule;
        boolean predefined = kind == ArtifactKind.PREDEFINED_VALIDATOR;
        Condition given = supplied ? ((SuppliedValidationRule) target).getCondition() : predefined ? null : rule.getCondition();
        List<Action> actions = supplied || predefined ? List.of() : rule.getActions();
        Action otherwise = supplied || predefined ? null : rule.getOtherwiseAction();

        List<MethodDefinition> methods = new ArrayList<>();
        if (rule.getPreCondition() != null && !predefined) methods.add(rule.getPreCondition().getDefinition());
        if (given != null) methods.add(given.getDefinition());
        for (Action action : actions) methods.add(action.getDefinition());
        if (otherwise != null) methods.add(otherwise.getDefinition());

        RuleDetails details = new RuleDetails(predefined ? null : expressions.of(rule.getPreCondition()), expressions.of(given),
                actions.stream().map(expressions::of).toList(), expressions.of(otherwise));

        return new Artifact(id, rule.getName(), ArtifactType.RULE, kind, registered,
                Sources.packageOf(sourceDefinition, kind == ArtifactKind.RULE_CLASS ? ruleClass : null),
                Expressions.blankToNull(def.getDescription()), source(sourceDefinition),
                includeSources && kind == ArtifactKind.RULE_CLASS ? ruleClass.getName() : null,
                Expressions.parameters(methods.toArray(new MethodDefinition[0])), details,
                target instanceof ValidationRule validationRule ? validation(validationRule, kind) : null, null, null);
    }

    private ArtifactKind ruleKind(RuleDefinition def, Object target, SourceDefinition source) {
        if (target instanceof ValueValidationRule && target.getClass().getName().startsWith(PREDEFINED_PACKAGE)) {
            return ArtifactKind.PREDEFINED_VALIDATOR;
        }
        if (Sources.isXml(source)) return ArtifactKind.XML_SCRIPT;
        Class<?> ruleClass = def.getRuleClass();
        if (ruleClass != null && !def.isInline() && !Rulii.isInternalClass(ruleClass)) return ArtifactKind.RULE_CLASS;
        return ArtifactKind.JAVA_BUILDER;
    }

    private ValidationDetails validation(ValidationRule rule, ArtifactKind kind) {
        String validator = null;
        ValueSource valueSource = null;
        Boolean failOnNull = null;
        Map<String, Object> settings = Map.of();

        if (rule instanceof ValueValidationRule value) {
            if (kind == ArtifactKind.PREDEFINED_VALIDATOR) validator = validatorName(value.getClass());
            valueSource = ValueValidationRuleBuilder.DEFAULT_VALUE_NAME.equals(value.getValueName())
                    ? ValueSource.expression(expressions.of(value.getValueFunction()))
                    : ValueSource.binding(value.getValueName());
            failOnNull = value.isFailOnNull();
            settings = settings(value);
        }

        return new ValidationDetails(validator, rule.getErrorCode(),
                rule.getSeverity() != null ? rule.getSeverity().name() : null,
                Expressions.blankToNull(rule.getErrorMessage()), Expressions.blankToNull(rule.getDefaultMessage()),
                valueSource, failOnNull, settings);
    }

    /** {@code EmailValidationRule} becomes {@code email}, {@code NotNullValidationRule} becomes {@code notNull}. */
    static String validatorName(Class<?> type) {
        String name = type.getSimpleName();
        if (name.endsWith("ValidationRule")) name = name.substring(0, name.length() - "ValidationRule".length());
        return name.isEmpty() ? type.getSimpleName() : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /** The settings a predefined validator exposes through its own getters (min, max, pattern, values ...). */
    private Map<String, Object> settings(ValueValidationRule rule) {
        Map<String, Object> settings = new TreeMap<>();
        for (Method method : rule.getClass().getMethods()) {
            if (!method.getDeclaringClass().getName().startsWith(PREDEFINED_PACKAGE)) continue;
            if (method.getParameterCount() != 0 || method.getReturnType() == void.class) continue;
            String property = propertyName(method.getName());
            if (property == null || "supportedTypes".equals(property)) continue;
            try {
                settings.put(property, plainValue(method.invoke(rule)));
            } catch (ReflectiveOperationException | RuntimeException e) {
                settings.put(property, "(unavailable)");
            }
        }
        return settings;
    }

    private static String propertyName(String methodName) {
        String rest;
        if (methodName.startsWith("get") && methodName.length() > 3) rest = methodName.substring(3);
        else if (methodName.startsWith("is") && methodName.length() > 2) rest = methodName.substring(2);
        else return null;
        return Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
    }

    private static Object plainValue(Object value) {
        if (value == null) return null;
        if (value instanceof String || value instanceof Boolean || value instanceof Integer || value instanceof Long
                || value instanceof Double || value instanceof Float || value instanceof Short || value instanceof Byte) return value;
        if (value instanceof BigDecimal decimal) return decimal.toPlainString();
        if (value instanceof Number number) return number.toString();
        if (value instanceof Enum<?> e) return e.name();
        if (value instanceof Class<?> c) return c.getName();
        if (value.getClass().isArray()) return Arrays.stream((Object[]) value).map(Describer::plainValue).toList();
        if (value instanceof Collection<?> collection) return collection.stream().map(Describer::plainValue).toList();
        return String.valueOf(value);
    }

    private Artifact ruleSet(String id, RuleSet<?> ruleSet, boolean registered) {
        RuleSetDefinition def = ruleSet.getDefinition();
        SourceDefinition sourceDefinition = def.getSource();
        ArtifactKind kind = Sources.isXml(sourceDefinition) ? ArtifactKind.XML : ArtifactKind.JAVA_BUILDER;

        List<String> members = new ArrayList<>();
        List<Rule> rules = ruleSet.getRules();
        for (int i = 0; i < rules.size(); i++) {
            String path = "members[" + i + "]";
            String memberId = idOfInstance(rules.get(i), id + "/" + path);
            members.add(memberId);
            references.add(new Reference(id, memberId, org.rulii.explorer.descriptor.ReferenceType.CONTAINS, false, path,
                    org.rulii.explorer.descriptor.Resolution.DIRECT));
        }

        RuleSetDetails details = new RuleSetDetails(def.isValidating(), expressions.hook(ruleSet.getPreCondition()),
                expressions.hook(ruleSet.getInitializer()), expressions.hook(ruleSet.getStopCondition()),
                expressions.hook(ruleSet.getFinalizer()), expressions.hook(ruleSet.getResultExtractor()),
                expressions.hook(ruleSet.getErrorHandler()), members);

        return new Artifact(id, ruleSet.getName(), ArtifactType.RULESET, kind, registered,
                Sources.packageOf(sourceDefinition, null), Expressions.blankToNull(ruleSet.getDescription()),
                source(sourceDefinition), null, inputParameters(def.getInputParameters(), def.isValidating()),
                null, null, details, null);
    }

    private Artifact ruleFlow(String id, RuleFlow<?> ruleFlow, boolean registered) {
        RuleFlowDefinition def = ruleFlow.getDefinition();
        SourceDefinition sourceDefinition = def.getSource();
        ArtifactKind kind = Sources.isXml(sourceDefinition) ? ArtifactKind.XML : ArtifactKind.JAVA_BUILDER;

        CommandMapper mapper = new CommandMapper(this, expressions, id);
        List<Command> commands = mapper.map(def.getCommands(), "commands");

        RuleFlowDetails details = new RuleFlowDetails(def.getContextLabel(), commands,
                mapper.handler(def.getGlobalHandler(), "globalHandler"), expressions.mapAction(def.getFinalizer()),
                expressions.mapFunction(def.getReturning()), Expressions.typeName(def.getResultType()));

        return new Artifact(id, ruleFlow.getName(), ArtifactType.RULEFLOW, kind, registered,
                Sources.packageOf(sourceDefinition, null), Expressions.blankToNull(def.getDescription()),
                source(sourceDefinition), null, inputParameters(def.getInputParameters(), false), null, null, null, details);
    }

    private List<Parameter> inputParameters(List<InputParameter<?>> parameters, boolean validating) {
        List<Parameter> result = new ArrayList<>();
        for (InputParameter<?> p : parameters) {
            if (validating && RULE_VIOLATIONS.equals(p.name())) continue; // added by validating() itself
            result.add(new Parameter(p.name(), Expressions.typeName(p.type()), p.required(), defaultText(p.defaultValue()),
                    null, Expressions.blankToNull(p.description())));
        }
        return result;
    }

    private String defaultText(Function<?> defaultValue) {
        if (defaultValue == null) return null;
        Expression expression = expressions.mapFunction(defaultValue.getExpression());
        return expression != null && expression.text() != null ? expression.text() : "(compiled)";
    }

    private Source source(SourceDefinition definition) {
        return includeSources ? Sources.of(definition) : null;
    }

    // -------------------------------------------------------------------------------------------
    // Lookups used by the command mapper
    // -------------------------------------------------------------------------------------------

    /**
     * The id of a runnable given as an instance: its registry name when registered, else an
     * inline artifact described now under the given path id.
     */
    String idOfInstance(Runnable<?> runnable, String pathId) {
        String id = ids.get(runnable);
        if (id != null) return id;
        ids.put(runnable, pathId);
        describe(pathId, runnable, false);
        return pathId;
    }

    /** The id a by-name lookup resolves to now, or null. */
    String idByName(String name) {
        try {
            Runnable<?> runnable = registry.get(name);
            return runnable == null ? null : ids.get(runnable);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The id a by-class lookup resolves to now, or null. Mirrors {@link RuleRegistry#getRule(Class)}. */
    String idByClass(Class<?> type) {
        try {
            List<Rule> matches = registry.getRules(type);
            return matches.isEmpty() ? null : ids.get(matches.get(0));
        } catch (RuntimeException e) {
            return null;
        }
    }

    void reference(Reference reference) {
        references.add(reference);
    }

    // -------------------------------------------------------------------------------------------
    // Derived sections
    // -------------------------------------------------------------------------------------------

    private List<PackageInfo> packages(List<Artifact> artifacts) {
        Map<String, PackageInfo> packages = new TreeMap<>();
        for (Artifact artifact : artifacts) {
            if (artifact.packageId() == null) continue;
            packages.putIfAbsent(artifact.packageId(), new PackageInfo(artifact.packageId(), Sources.packageKind(artifact.kind())));
        }
        return new ArrayList<>(packages.values());
    }

    private List<BindingUsage> bindings(List<Artifact> artifacts) {
        Map<String, Set<String>> readBy = new TreeMap<>();
        Map<String, Set<String>> writtenBy = new TreeMap<>();
        Map<String, Set<String>> unknown = new TreeMap<>();

        for (Artifact artifact : artifacts) {
            Set<String> reads = new TreeSet<>();
            Set<String> writes = new TreeSet<>();
            boolean compiledWrites = false;

            for (Parameter parameter : artifact.parameters()) reads.add(parameter.name());

            if (artifact.validation() != null && artifact.validation().valueSource() != null
                    && artifact.validation().valueSource().name() != null) {
                reads.add(root(artifact.validation().valueSource().name()));
            }

            for (Expression expression : expressionsOf(artifact)) {
                expression.reads().forEach(path -> reads.add(root(path)));
                expression.writes().forEach(path -> writes.add(root(path)));
            }

            if (artifact.rule() != null) {
                compiledWrites = artifact.rule().then().stream().anyMatch(Expressions::hasCompiledPart)
                        || Expressions.hasCompiledPart(artifact.rule().otherwise());
            }

            if (artifact.ruleFlow() != null) {
                boolean[] compiled = {false};
                Commands.walk(artifact.ruleFlow(), (path, command) -> {
                    if (command.bind() != null) command.bind().names().forEach(n -> writes.add(n.name()));
                    if (command.as() != null) writes.add(command.as());
                    if (command.item() != null) writes.add(command.item());
                    if (command.thenRun() != null) writes.add(command.thenRun().as());
                    if (command.type() == org.rulii.explorer.descriptor.CommandType.CUSTOM) compiled[0] = true;
                    if ((command.type() == org.rulii.explorer.descriptor.CommandType.EXECUTE
                            || command.type() == org.rulii.explorer.descriptor.CommandType.APPLY)
                            && Expressions.hasCompiledPart(command.expression())) compiled[0] = true;
                });
                compiledWrites = compiled[0];
            }

            for (String name : reads) readBy.computeIfAbsent(name, n -> new TreeSet<>()).add(artifact.id());
            for (String name : writes) writtenBy.computeIfAbsent(name, n -> new TreeSet<>()).add(artifact.id());
            if (compiledWrites) for (String name : reads) unknown.computeIfAbsent(name, n -> new TreeSet<>()).add(artifact.id());
        }

        Set<String> names = new TreeSet<>(readBy.keySet());
        names.addAll(writtenBy.keySet());

        List<BindingUsage> result = new ArrayList<>();
        for (String name : names) {
            result.add(new BindingUsage(name, list(readBy.get(name)), list(writtenBy.get(name)), list(unknown.get(name))));
        }
        return result;
    }

    private static List<String> list(Set<String> set) {
        return set == null ? List.of() : new ArrayList<>(set);
    }

    private static String root(String path) {
        int dot = path.indexOf('.');
        int bracket = path.indexOf('[');
        int end = dot < 0 ? bracket : bracket < 0 ? dot : Math.min(dot, bracket);
        return end < 0 ? path : path.substring(0, end);
    }

    /** Every expression an artifact carries, for binding analysis. */
    private static List<Expression> expressionsOf(Artifact artifact) {
        List<Expression> result = new ArrayList<>();
        if (artifact.rule() != null) {
            add(result, artifact.rule().preCondition(), artifact.rule().given(), artifact.rule().otherwise());
            result.addAll(artifact.rule().then());
        }
        if (artifact.validation() != null && artifact.validation().valueSource() != null) {
            add(result, artifact.validation().valueSource().expression());
        }
        if (artifact.ruleSet() != null) {
            RuleSetDetails rs = artifact.ruleSet();
            add(result, rs.preCondition(), rs.initializer(), rs.stopCondition(), rs.finalizer(), rs.resultExtractor(), rs.errorHandler());
        }
        if (artifact.ruleFlow() != null) {
            RuleFlowDetails flow = artifact.ruleFlow();
            add(result, flow.finalizer(), flow.returning());
            Commands.walk(flow, (path, command) -> {
                add(result, command.expression(), command.condition(), command.source(), command.stop());
                if (command.bind() != null) command.bind().names().forEach(n -> add(result, n.expression()));
            });
        }
        return result;
    }

    private static void add(List<Expression> into, Expression... expressions) {
        for (Expression expression : expressions) if (expression != null) into.add(expression);
    }

    // -------------------------------------------------------------------------------------------
    // Failures
    // -------------------------------------------------------------------------------------------

    private void undescribable(String id, RuntimeException e) {
        String cause = e.getMessage() != null ? e.getClass().getSimpleName() + ": " + e.getMessage() : e.getClass().getName();
        problems.add(new Problem(ProblemSeverity.ERROR, ProblemCodes.UNDESCRIBABLE, id, null,
                "Could not describe this artifact (" + cause + "). It is shown with what could be read."));
    }

    private static String safeName(Runnable<?> runnable, String fallback) {
        try {
            String name = runnable.getName();
            return name != null ? name : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static ArtifactType typeOf(Runnable<?> runnable) {
        if (runnable instanceof RuleSet<?>) return ArtifactType.RULESET;
        if (runnable instanceof RuleFlow<?>) return ArtifactType.RULEFLOW;
        return ArtifactType.RULE;
    }
}
