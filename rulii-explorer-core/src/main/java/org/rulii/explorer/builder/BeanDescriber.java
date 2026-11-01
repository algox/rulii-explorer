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

import org.rulii.explorer.beans.ConstraintPhrases;
import org.rulii.explorer.descriptor.BeanInfo;
import org.rulii.explorer.descriptor.BeanKind;
import org.rulii.explorer.descriptor.BeanRule;
import org.rulii.explorer.descriptor.BindKind;
import org.rulii.explorer.descriptor.CascadeInfo;
import org.rulii.explorer.descriptor.ConstraintInfo;
import org.rulii.explorer.descriptor.ContainerElementInfo;
import org.rulii.explorer.descriptor.ContextBindInfo;
import org.rulii.explorer.descriptor.CrossParameterInfo;
import org.rulii.explorer.descriptor.DeclaredOn;
import org.rulii.explorer.descriptor.Discovery;
import org.rulii.explorer.descriptor.ExecutableInfo;
import org.rulii.explorer.descriptor.ExecutableKind;
import org.rulii.explorer.descriptor.ExprInfo;
import org.rulii.explorer.descriptor.Expression;
import org.rulii.explorer.descriptor.FlowRef;
import org.rulii.explorer.descriptor.GroupConversionInfo;
import org.rulii.explorer.descriptor.ParameterInfo;
import org.rulii.explorer.descriptor.Placement;
import org.rulii.explorer.descriptor.PlacementKind;
import org.rulii.explorer.descriptor.Problem;
import org.rulii.explorer.descriptor.ProblemSeverity;
import org.rulii.explorer.descriptor.PropertyInfo;
import org.rulii.explorer.descriptor.Reference;
import org.rulii.explorer.descriptor.ReferenceType;
import org.rulii.explorer.descriptor.Resolution;
import org.rulii.explorer.descriptor.ReturnValueInfo;
import org.rulii.explorer.descriptor.Source;
import org.rulii.explorer.descriptor.ValidationInfo;
import org.rulii.explorer.descriptor.ValidationTarget;
import org.rulii.explorer.problem.ProblemCodes;
import org.rulii.lib.spring.core.annotation.AnnotationUtils;
import org.rulii.model.ExpressionInfo;
import org.rulii.model.SourceDefinition;
import org.rulii.model.condition.Condition;
import org.rulii.validation.beans.BeanValidator;
import org.rulii.validation.beans.assembly.BeanRules;
import org.rulii.validation.beans.assembly.ExecutableRules;
import org.rulii.validation.beans.assembly.ExprValidationRule;
import org.rulii.validation.beans.assembly.MaterializedRule;
import org.rulii.validation.beans.assembly.PlannedBind;
import org.rulii.validation.beans.assembly.RulePlacement;
import org.rulii.validation.beans.extract.ValueExtractorDescriptor;
import org.rulii.validation.beans.model.BeanDefinition;
import org.rulii.validation.beans.model.BindDefinition;
import org.rulii.validation.beans.model.ConstraintDefinition;
import org.rulii.validation.beans.model.ConstraintSource;
import org.rulii.validation.beans.model.ContainerElementDefinition;
import org.rulii.validation.beans.model.DeclarationConstraintSource;
import org.rulii.validation.beans.model.ExecutableDefinition;
import org.rulii.validation.beans.model.ExprDefinition;
import org.rulii.validation.beans.model.GroupConversions;
import org.rulii.validation.beans.model.ParameterDefinition;
import org.rulii.validation.beans.model.PropertyDefinition;
import org.rulii.validation.beans.model.ReturnValueDefinition;
import org.rulii.validation.beans.model.RuleDeclaration;
import org.rulii.validation.beans.model.ValidateDefinition;
import org.rulii.validation.beans.model.ValueDeclaration;
import org.rulii.validation.beans.traversal.RuleFlowAssembler;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Describes the beans a {@link BeanSource} names (descriptor 1.1, section 17): their declarations
 * from the library's definitions, the rules it materialized from them, the references they make
 * ({@code cascades} between beans, {@code runs} for {@code @Validate}) and their problems. Adds
 * nothing to {@code artifacts}: a bean's rules stay with the bean.
 *
 * <p>Lives next to {@link Describer} for its package-private helpers, and is the only class here
 * that names a rulii-bean-validation type. The JVM links it when {@link Describer} first runs it,
 * which happens only when a source is configured, so the library stays optional.
 *
 * <p>Reading a bean's rules through the validator warms its cache for that class, as validating
 * it would.
 *
 * @author Algorithmx Development Team
 * @since 1.1
 */
final class BeanDescriber {

    private static final String PARAMETERS_FLOW_SUFFIX = "ParametersFlow";
    private static final Set<String> NOT_ATTRIBUTES = Set.of("message", "groups", "payload");

    private final Describer describer;
    private final Expressions expressions;
    private final boolean includeSources;
    private final BeanValidator validator;

    /** The working set: the source's classes and, as they are found, the cascade targets. */
    private final Map<Class<?>, Discovery> classes = new LinkedHashMap<>();
    private final Map<Class<?>, Read> read = new LinkedHashMap<>();

    /** Per bean: the id of the rule each declaration became, and the rule itself. */
    private final Map<RuleDeclaration, String> ruleIds = new IdentityHashMap<>();
    private final Map<RuleDeclaration, MaterializedRule> rules = new IdentityHashMap<>();
    private Class<?> bean;
    private String beanId;

    BeanDescriber(Describer describer, Expressions expressions, boolean includeSources, BeanSource source) {
        super();
        this.describer = describer;
        this.expressions = expressions;
        this.includeSources = includeSources;
        if (!(source.validator() instanceof BeanValidator)) {
            throw new IllegalArgumentException("the bean source's validator must be a " + BeanValidator.class.getName()
                    + " but is " + (source.validator() == null ? "null" : source.validator().getClass().getName()) + ".");
        }
        this.validator = (BeanValidator) source.validator();
        this.classes.putAll(source.classes());
    }

    List<BeanInfo> describe() {
        Deque<Class<?>> pending = new ArrayDeque<>(classes.keySet());

        while (!pending.isEmpty()) {
            Class<?> type = pending.poll();
            if (read.containsKey(type)) continue;
            Read entry = read(type, classes.get(type));
            read.put(type, entry);

            if (entry.definition == null) continue;
            for (Class<?> target : cascadeTargets(entry.definition)) {
                if (classes.containsKey(target) || !BeanDefinition.isCandidate(target)) continue;
                classes.put(target, Discovery.CASCADE);
                pending.add(target);
            }
        }

        List<Class<?>> sorted = new ArrayList<>(read.keySet());
        sorted.sort((a, b) -> Rulii.compareNatural(a.getName(), b.getName()));

        List<BeanInfo> result = new ArrayList<>();
        for (Class<?> type : sorted) {
            result.add(bean(read.get(type)));
        }
        return result;
    }

    // -------------------------------------------------------------------------------------------
    // Reading: definitions and materialized rules, with the failures they report
    // -------------------------------------------------------------------------------------------

    /** What the library gave for one class: the rules when it could build them, else the definition alone. */
    private record Read(Class<?> type, Discovery discovery, BeanDefinition definition, BeanRules rules,
                        Map<ExecutableDefinition, ExecutableRules> executables) {
    }

    private Read read(Class<?> type, Discovery discovery) {
        String id = type.getName();
        BeanRules beanRules = null;
        BeanDefinition definition = null;
        Map<ExecutableDefinition, ExecutableRules> executables = new LinkedHashMap<>();

        try {
            beanRules = validator.getBeanRules(type);
            definition = beanRules.getDefinition();
        } catch (RuntimeException e) {
            try {
                definition = BeanDefinition.load(type, validator.getValueExtractorRegistry(), validator.getParameterNameProvider());
                problem(ProblemSeverity.ERROR, ProblemCodes.BEAN_NOT_MATERIALIZED, id, null,
                        "The library could not build this bean's rules (" + cause(e) + "). It is shown from its declarations alone.");
            } catch (RuntimeException failure) {
                problem(ProblemSeverity.ERROR, ProblemCodes.UNDESCRIBABLE, id, null,
                        "Could not describe this bean (" + cause(failure) + "). It is shown with what could be read.");
            }
        }

        if (discovery == Discovery.CONFIGURED && !BeanDefinition.isCandidate(type)) {
            problem(ProblemSeverity.WARNING, ProblemCodes.UNDESCRIBABLE, id, null,
                    "Configured as a bean, but declares no constraint, @Valid, @Validate, @Expr or @Bind.");
        }

        if (definition != null && beanRules != null) {
            List<ExecutableDefinition> all = new ArrayList<>(definition.getConstructors());
            all.addAll(definition.getMethods());
            for (ExecutableDefinition executable : all) {
                try {
                    executables.put(executable, validator.getExecutableRules(type, executable.getExecutable()));
                } catch (RuntimeException e) {
                    problem(ProblemSeverity.ERROR, ProblemCodes.BEAN_NOT_MATERIALIZED, id, "executables." + key(definition, executable),
                            "The library could not build the rules of " + signature(executable) + " (" + cause(e) + "). It is shown from its declarations alone.");
                }
            }
        }

        return new Read(type, discovery, definition, beanRules, executables);
    }

    private List<Class<?>> cascadeTargets(BeanDefinition definition) {
        List<Class<?>> targets = new ArrayList<>();
        for (PropertyDefinition property : definition.getProperties()) {
            if (property.isCascaded()) add(targets, property.getGenericType());
            cascadeTargets(property.getContainerElements(), targets);
        }
        List<ExecutableDefinition> executables = new ArrayList<>(definition.getConstructors());
        executables.addAll(definition.getMethods());
        for (ExecutableDefinition executable : executables) {
            for (ParameterDefinition parameter : executable.getParameters()) {
                if (parameter.isCascaded()) add(targets, parameter.getGenericType());
                cascadeTargets(parameter.getContainerElements(), targets);
            }
            if (executable.hasReturnValue()) {
                ReturnValueDefinition returnValue = executable.getReturnValue();
                if (returnValue.isCascaded()) add(targets, returnValue.getGenericType());
                cascadeTargets(returnValue.getContainerElements(), targets);
            }
        }
        return targets;
    }

    private void cascadeTargets(List<ContainerElementDefinition> elements, List<Class<?>> targets) {
        for (ContainerElementDefinition element : elements) {
            if (element.isCascaded()) add(targets, element.getType());
            cascadeTargets(element.getChildren(), targets);
        }
    }

    private void add(List<Class<?>> targets, Type type) {
        Class<?> target = cascadeTarget(type);
        if (target != null && !target.isPrimitive() && !target.isArray() && !target.getName().startsWith("java.")) targets.add(target);
    }

    /**
     * The type a {@code @Valid} on a value of the given type validates: the type itself, or, when
     * it is a container, what the library's extractor for it serves (the values of a {@code Map},
     * the single extractable position of anything else), recursively. Mirrors the library's
     * legacy container cascade; null when the type is not a class.
     */
    private Class<?> cascadeTarget(Type type) {
        Class<?> raw = raw(type);
        if (raw == null) return null;
        if (raw.isArray()) return cascadeTarget(raw.getComponentType());

        ValueExtractorDescriptor extractor = legacyExtractor(raw);
        if (extractor == null) return raw;
        if (extractor.getTypeArgumentIndex() == ValueExtractorDescriptor.CONTAINER) return extractor.getExtractedType();
        if (type instanceof ParameterizedType parameterized && extractor.getTypeArgumentIndex() < parameterized.getActualTypeArguments().length) {
            return cascadeTarget(parameterized.getActualTypeArguments()[extractor.getTypeArgumentIndex()]);
        }
        return extractor.getExtractedType() != null ? extractor.getExtractedType() : raw;
    }

    private ValueExtractorDescriptor legacyExtractor(Class<?> type) {
        List<ValueExtractorDescriptor> candidates = validator.getValueExtractorRegistry().find(type);
        if (candidates.isEmpty()) return null;
        if (Map.class.isAssignableFrom(type)) {
            for (ValueExtractorDescriptor candidate : candidates) {
                if (Map.class.isAssignableFrom(candidate.getContainerType()) && candidate.getTypeArgumentIndex() == 1) return candidate;
            }
            return null;
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static Class<?> raw(Type type) {
        if (type instanceof Class<?> c) return c;
        if (type instanceof ParameterizedType p && p.getRawType() instanceof Class<?> c) return c;
        return null;
    }

    // -------------------------------------------------------------------------------------------
    // The bean
    // -------------------------------------------------------------------------------------------

    private BeanInfo bean(Read entry) {
        bean = entry.type();
        beanId = bean.getName();
        ruleIds.clear();
        rules.clear();

        BeanDefinition definition = entry.definition();
        if (definition == null) {
            return new BeanInfo(beanId, bean.getSimpleName(), bean.getPackageName(), kind(bean), entry.discovery(), null, null, false,
                    new FlowRef(RuleFlowAssembler.flowName(bean)), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        // Rules first: the declarations point at them.
        List<BeanRule> beanRules = entry.rules() == null ? List.of() : beanRules(entry.rules().getRules(), beanId + "/rules/");
        Map<ExecutableDefinition, List<BeanRule>> executableRules = new LinkedHashMap<>();
        entry.executables().forEach((executable, rules) -> executableRules.put(executable,
                rules == null ? List.of() : beanRules(rules.getRules(), beanId + "/" + key(definition, executable) + "/rules/")));

        Map<String, PlannedBind> planned = entry.rules() == null ? Map.of() : planned(entry.rules().getBindingPlan().getBinds());

        List<PropertyInfo> properties = new ArrayList<>();
        for (PropertyDefinition property : definition.getProperties()) {
            properties.add(property(property));
        }

        List<ExecutableInfo> executables = new ArrayList<>();
        List<ExecutableDefinition> all = new ArrayList<>(definition.getConstructors());
        all.addAll(definition.getMethods());
        for (ExecutableDefinition executable : all) {
            executables.add(executable(definition, executable, entry.executables().get(executable), executableRules.getOrDefault(executable, List.of())));
        }
        executables.sort((a, b) -> Rulii.compareNatural(a.key(), b.key()));

        return new BeanInfo(beanId, definition.getName(), bean.getPackageName(), kind(bean), entry.discovery(),
                source(definition.getSource()), groupSequence(definition), entry.rules() != null,
                new FlowRef(RuleFlowAssembler.flowName(bean)), constraints(definition.getClassConstraints()),
                validations(definition.getValidateRules(), ""), expressions(definition.getExprRules()),
                binds(definition.getBinds(), planned, false), properties, executables, beanRules);
    }

    private static BeanKind kind(Class<?> type) {
        if (type.isRecord()) return BeanKind.RECORD;
        if (type.isInterface()) return BeanKind.INTERFACE;
        if (Modifier.isAbstract(type.getModifiers())) return BeanKind.ABSTRACT_CLASS;
        return BeanKind.CLASS;
    }

    private static List<String> groupSequence(BeanDefinition definition) {
        List<Class<?>> sequence = definition.getDefaultGroupSequence();
        return sequence == null ? null : names(sequence);
    }

    // -------------------------------------------------------------------------------------------
    // Members
    // -------------------------------------------------------------------------------------------

    private PropertyInfo property(PropertyDefinition property) {
        ValueDeclaration declaration = declaration(property);
        String path = "properties." + property.getName();
        return new PropertyInfo(property.getName(), Expressions.typeName(property.getType()), Expressions.typeName(property.getGenericType()),
                declaredOn(declaration), declaringClass(declaration.getDeclaringClass()), source(property.getSource()),
                constraints(property.getConstraints()), validations(property.getValidateRules(), path + "."),
                expressions(property.getExprRules()),
                cascade(property.isCascaded(), property.getGenericType(), property.getGroupConversions(), path),
                elements(property.getContainerElements(), path));
    }

    /**
     * The declaration a property is described by: the one its first constraint, {@code @Validate}
     * or {@code @Expr} is written on; for a property that is only cascaded or carries only
     * container-element declarations, the one the library reads the value from. Decides the
     * property's {@code declaredOn} and {@code declaringClass}.
     */
    private static ValueDeclaration declaration(PropertyDefinition property) {
        List<ConstraintSource> sources = new ArrayList<>();
        for (ConstraintDefinition constraint : property.getConstraints()) {
            sources.add(constraint.getConstraintSource());
        }
        for (ValidateDefinition validation : property.getValidateRules()) {
            sources.add(validation.getDeclarationSource());
        }
        for (ExprDefinition expr : property.getExprRules()) {
            sources.add(expr.getConstraintSource());
        }

        for (ConstraintSource source : sources) {
            if (source instanceof DeclarationConstraintSource declared) return declared.getDeclaration();
        }
        return property.getPropertySource().getPrimary();
    }

    private static DeclaredOn declaredOn(ValueDeclaration declaration) {
        return switch (declaration.getKind()) {
            case GETTER -> DeclaredOn.GETTER;
            case RECORD_COMPONENT -> DeclaredOn.RECORD_COMPONENT;
            default -> DeclaredOn.FIELD;
        };
    }

    private ExecutableInfo executable(BeanDefinition owner, ExecutableDefinition executable, ExecutableRules executableRules, List<BeanRule> rules) {
        String key = key(owner, executable);
        String path = "executables." + key + ".";
        Map<String, PlannedBind> parameterBinds = executableRules == null ? Map.of() : planned(executableRules.getBindingPlan().getParameterBinds());
        Map<String, PlannedBind> returnValueBinds = executableRules == null ? Map.of() : planned(executableRules.getBindingPlan().getReturnValueBinds());

        List<ParameterInfo> parameters = new ArrayList<>();
        for (ParameterDefinition parameter : executable.getParameters()) {
            String parameterPath = path + "parameters[" + parameter.getIndex() + "]";
            parameters.add(new ParameterInfo(parameter.getIndex(), parameter.getName(), Expressions.typeName(parameter.getType()),
                    Expressions.typeName(parameter.getGenericType()), constraints(parameter.getConstraints()), List.of(), List.of(),
                    cascade(parameter.isCascaded(), parameter.getGenericType(), parameter.getGroupConversions(), parameterPath),
                    elements(parameter.getContainerElements(), parameterPath)));
        }

        CrossParameterInfo crossParameter = new CrossParameterInfo(constraints(executable.getCrossParameterConstraints()),
                validations(executable.getValidateRules(), path + "crossParameter."), expressions(executable.getExprRules()));
        if (crossParameter.constraints().isEmpty() && crossParameter.validations().isEmpty() && crossParameter.expressions().isEmpty()) {
            crossParameter = null;
        }

        ReturnValueInfo returnValue = null;
        if (executable.hasReturnValue()) {
            ReturnValueDefinition definition = executable.getReturnValue();
            if (definition.isConstrained() || !definition.getBinds().isEmpty()) {
                String returnPath = path + "returnValue";
                returnValue = new ReturnValueInfo(Expressions.typeName(definition.getType()), Expressions.typeName(definition.getGenericType()),
                        constraints(definition.getConstraints()), validations(definition.getValidateRules(), returnPath + "."),
                        expressions(definition.getExprRules()),
                        cascade(definition.isCascaded(), definition.getGenericType(), definition.getGroupConversions(), returnPath),
                        elements(definition.getContainerElements(), returnPath), binds(definition.getBinds(), returnValueBinds, true));
            }
        }

        return new ExecutableInfo(executable.getName(), key, kind(executable.getKind()), signature(executable), source(executable.getSource()),
                parameters, crossParameter, returnValue, binds(executable.getBinds(), parameterBinds, true),
                executable.hasConstrainedParameters() ? new FlowRef(RuleFlowAssembler.parametersFlowName(owner, executable)) : null,
                executable.hasConstrainedReturnValue() ? new FlowRef(RuleFlowAssembler.returnValueFlowName(owner, executable)) : null,
                rules);
    }

    private static ExecutableKind kind(ExecutableDefinition.Kind kind) {
        return switch (kind) {
            case CONSTRUCTOR -> ExecutableKind.CONSTRUCTOR;
            case GETTER -> ExecutableKind.GETTER;
            default -> ExecutableKind.METHOD;
        };
    }

    /** The library's flow-name stem: {@code order_placeOrder}, unique within the bean. */
    private static String key(BeanDefinition owner, ExecutableDefinition executable) {
        String name = RuleFlowAssembler.parametersFlowName(owner, executable);
        return name.endsWith(PARAMETERS_FLOW_SUFFIX) ? name.substring(0, name.length() - PARAMETERS_FLOW_SUFFIX.length()) : name;
    }

    private static String signature(ExecutableDefinition executable) {
        return executable.getName() + "(" + Arrays.stream(executable.getParameterTypes()).map(Class::getName).collect(Collectors.joining(", ")) + ")";
    }

    private List<ContainerElementInfo> elements(List<ContainerElementDefinition> elements, String path) {
        return elements(elements, path, ".containerElements[");
    }

    private List<ContainerElementInfo> elements(List<ContainerElementDefinition> elements, String path, String segment) {
        List<ContainerElementInfo> result = new ArrayList<>();
        for (int i = 0; i < elements.size(); i++) {
            ContainerElementDefinition element = elements.get(i);
            String elementPath = path + segment + i + "]";
            result.add(new ContainerElementInfo(element.getName(), element.getContainerType().getName(), element.getTypeArgumentIndex(),
                    Expressions.typeName(element.getType()), constraints(element.getConstraints()),
                    cascade(element.isCascaded(), element.getType(), element.getGroupConversions(), elementPath),
                    elements(element.getChildren(), elementPath, ".children[")));
        }
        return result;
    }

    /** A cascade, with the {@code cascades} reference when the target is a described bean. */
    private CascadeInfo cascade(boolean cascaded, Type type, GroupConversions conversions, String path) {
        if (!cascaded) return null;
        Class<?> target = cascadeTarget(type);
        String targetId = target != null && read.containsKey(target) ? target.getName() : null;
        if (targetId != null) describer.reference(new Reference(beanId, targetId, ReferenceType.CASCADES, false, path, Resolution.DIRECT));

        List<GroupConversionInfo> groupConversions = new ArrayList<>();
        if (conversions != null) {
            conversions.asMap().forEach((from, to) -> groupConversions.add(new GroupConversionInfo(from.getSimpleName(), to.getSimpleName())));
        }
        return new CascadeInfo(targetId, Expressions.typeName(type), groupConversions);
    }

    // -------------------------------------------------------------------------------------------
    // Declarations
    // -------------------------------------------------------------------------------------------

    private List<ConstraintInfo> constraints(List<ConstraintDefinition> constraints) {
        List<ConstraintInfo> result = new ArrayList<>();
        for (ConstraintDefinition constraint : constraints) {
            result.add(constraint(constraint));
        }
        return result;
    }

    private ConstraintInfo constraint(ConstraintDefinition constraint) {
        Class<?> type = constraint.getAnnotationType();
        Map<String, Object> attributes = new TreeMap<>();
        AnnotationUtils.getAnnotationAttributes(constraint.getAnnotation()).forEach((name, value) -> {
            if (!NOT_ATTRIBUTES.contains(name)) attributes.put(name, Describer.plainValue(value));
        });

        return new ConstraintInfo(type.getName(), decapitalize(type.getSimpleName()), attributes,
                ConstraintPhrases.phrase(type.getName(), attributes), Expressions.blankToNull(constraint.getMessage()),
                names(constraint.getGroups()), constraint.getSeverity().name(), names(constraint.getPayload()),
                constraint.isReportAsSingleViolation(), constraints(constraint.getComposingConstraints()), ruleIds.get(constraint));
    }

    /** {@code @Validate}, each name resolved against the registry described, with the {@code runs} reference when it resolves. */
    private List<ValidationInfo> validations(List<ValidateDefinition> validations, String pathPrefix) {
        List<ValidationInfo> result = new ArrayList<>();
        for (int i = 0; i < validations.size(); i++) {
            ValidateDefinition validation = validations.get(i);
            String path = pathPrefix + "validations[" + i + "]";
            List<ValidationTarget> targets = new ArrayList<>();
            for (String name : validation.getRuleNames()) {
                String artifactId = describer.idByName(name);
                if (artifactId != null) describer.reference(new Reference(beanId, artifactId, ReferenceType.RUNS, false, path, Resolution.BY_NAME));
                targets.add(new ValidationTarget(name, artifactId));
            }
            result.add(new ValidationInfo(validation.getRuleNames(), names(validation.getGroups()), validation.getApplyTo().name(), targets));
        }
        return result;
    }

    /** {@code @Expr}: the script as the materialized rule evaluates it, else as declared. */
    private List<ExprInfo> expressions(List<ExprDefinition> exprs) {
        List<ExprInfo> result = new ArrayList<>();
        for (ExprDefinition expr : exprs) {
            MaterializedRule rule = rules.get(expr);
            Expression expression = rule != null && rule.getRule().getTarget() instanceof ExprValidationRule exprRule
                    ? expressions.of(exprRule.getCondition())
                    : expressions.map(ExpressionInfo.script(expr.getLanguage(), expr.getScript()));
            result.add(new ExprInfo(expression, expr.getErrorCode(), Expressions.blankToNull(expr.getMessage()), expr.getSeverity().name(),
                    names(expr.getGroups()), expr.getApplyTo().name(), expr.isSkipNull(), ruleIds.get(expr)));
        }
        return result;
    }

    /** {@code @Bind}: the literal or the expression, typed as planned when the bean materialized. */
    private List<ContextBindInfo> binds(List<BindDefinition> binds, Map<String, PlannedBind> planned, boolean executable) {
        List<ContextBindInfo> result = new ArrayList<>();
        for (BindDefinition bind : binds) {
            PlannedBind plan = planned.get(bind.getBindingName());
            String language = plan != null && plan.getScript() != null ? plan.getScript().getLanguageName() : bind.getLanguage();
            Expression expression = bind.isExpression() ? expressions.map(ExpressionInfo.script(language, bind.getScript())) : null;
            String type = plan != null ? plan.getType().getName() : bind.getType().getName();
            result.add(new ContextBindInfo(bind.getBindingName(), type, bind.isExpression() ? BindKind.EXPRESSION : BindKind.LITERAL,
                    bind.getLiteral(), expression, executable ? bind.getApplyTo().name() : null,
                    declaringClass(bind.getDeclarationSource().getDeclaringClass())));
        }
        return result;
    }

    private static Map<String, PlannedBind> planned(List<PlannedBind> binds) {
        Map<String, PlannedBind> result = new LinkedHashMap<>();
        for (PlannedBind bind : binds) {
            result.put(bind.getName(), bind);
        }
        return result;
    }

    // -------------------------------------------------------------------------------------------
    // Materialized rules
    // -------------------------------------------------------------------------------------------

    private List<BeanRule> beanRules(List<MaterializedRule> materialized, String idPrefix) {
        List<BeanRule> result = new ArrayList<>();
        for (MaterializedRule rule : materialized) {
            result.add(beanRule(rule, idPrefix));
        }
        return result;
    }

    private BeanRule beanRule(MaterializedRule rule, String idPrefix) {
        String id = idPrefix + rule.getName();
        ruleIds.putIfAbsent(rule.getDefinition(), id);
        rules.putIfAbsent(rule.getDefinition(), rule);

        Condition given = rule.getRule().getTarget() instanceof ExprValidationRule expr ? expr.getCondition() : null;
        Describer.RuleBody body = describer.beanRuleBody(rule.getRule(), given);

        return new BeanRule(id, rule.getName(), placement(rule.getPlacement()), body.kind(), body.parameters(), body.rule(),
                body.validation(), beanRules(rule.getHostedRules(), idPrefix));
    }

    private static Placement placement(RulePlacement placement) {
        PlacementKind kind = switch (placement.getKind()) {
            case PROPERTY -> PlacementKind.PROPERTY;
            case PARAMETER -> PlacementKind.PARAMETER;
            case RETURN_VALUE -> PlacementKind.RETURN_VALUE;
            case CROSS_PARAMETER -> PlacementKind.CROSS_PARAMETER;
            default -> PlacementKind.BEAN;
        };
        return new Placement(kind, placement.getName(), placement.getIndex() >= 0 ? placement.getIndex() : null);
    }

    // -------------------------------------------------------------------------------------------
    // Small helpers
    // -------------------------------------------------------------------------------------------

    private Source source(SourceDefinition definition) {
        return includeSources ? Sources.of(definition) : null;
    }

    /** The declaring class when it is not the bean itself; hidden with the sources (NFR-4). */
    private String declaringClass(Class<?> declaringClass) {
        return includeSources && declaringClass != null && declaringClass != bean ? declaringClass.getName() : null;
    }

    private static List<String> names(Collection<? extends Class<?>> types) {
        List<String> names = new ArrayList<>();
        for (Class<?> type : types) {
            names.add(type.getSimpleName());
        }
        return names;
    }

    private static String decapitalize(String name) {
        return name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private void problem(ProblemSeverity severity, String code, String id, String path, String message) {
        describer.problem(new Problem(severity, code, id, path, message));
    }

    private static String cause(RuntimeException e) {
        return e.getMessage() != null ? e.getClass().getSimpleName() + ": " + e.getMessage() : e.getClass().getName();
    }
}
