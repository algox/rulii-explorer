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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.rulii.explorer.beans.Beans;
import org.rulii.explorer.descriptor.ArtifactKind;
import org.rulii.explorer.descriptor.BeanInfo;
import org.rulii.explorer.descriptor.BeanKind;
import org.rulii.explorer.descriptor.BeanRule;
import org.rulii.explorer.descriptor.BindKind;
import org.rulii.explorer.descriptor.ConstraintInfo;
import org.rulii.explorer.descriptor.ContainerElementInfo;
import org.rulii.explorer.descriptor.DeclaredOn;
import org.rulii.explorer.descriptor.Descriptor;
import org.rulii.explorer.descriptor.Discovery;
import org.rulii.explorer.descriptor.ExecutableInfo;
import org.rulii.explorer.descriptor.ExecutableKind;
import org.rulii.explorer.descriptor.ExpressionKind;
import org.rulii.explorer.descriptor.PlacementKind;
import org.rulii.explorer.descriptor.Problem;
import org.rulii.explorer.descriptor.ProblemSeverity;
import org.rulii.explorer.descriptor.PropertyInfo;
import org.rulii.explorer.descriptor.Reference;
import org.rulii.explorer.descriptor.ReferenceType;
import org.rulii.explorer.descriptor.Resolution;
import org.rulii.explorer.descriptor.SourceType;
import org.rulii.explorer.descriptor.ValueSource;
import org.rulii.explorer.fixture.OrderFixture;
import org.rulii.explorer.fixture.beans.Address;
import org.rulii.explorer.fixture.beans.Broken;
import org.rulii.explorer.fixture.beans.Customer;
import org.rulii.explorer.fixture.beans.Line;
import org.rulii.explorer.fixture.beans.Order;
import org.rulii.explorer.fixture.beans.Plain;
import org.rulii.explorer.fixture.beans.Priced;
import org.rulii.explorer.fixture.beans.Shape;
import org.rulii.explorer.fixture.beans.Staged;
import org.rulii.explorer.problem.ProblemCodes;
import org.rulii.validation.beans.BeanValidator;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The beans section built from the bean fixture (descriptor 1.1, section 17): discovery, every
 * declaration kind, the materialized rules and their ids, references, problems.
 */
class BeanDescriberTest {

    private static final String PACKAGE = "org.rulii.explorer.fixture.beans";

    private static OrderFixture fixture;
    private static Descriptor descriptor;

    @BeforeAll
    static void build() {
        fixture = new OrderFixture();
        BeanValidator validator = BeanValidator.builder().registry(fixture.registry()).build();
        descriptor = DescriptorBuilder.of(fixture.registry())
                .applicationName("order-service")
                .analyzers(List.of(OrderFixture.stubAnalyzer()))
                .beans(Beans.of(validator)
                        .scan(List.of(PACKAGE), BeanDescriberTest.class.getClassLoader())
                        .classes(List.of(Plain.class))
                        .build())
                .build();
    }

    private static BeanInfo bean(Class<?> type) {
        return descriptor.beans().stream().filter(b -> b.id().equals(type.getName())).findFirst()
                .orElseThrow(() -> new AssertionError("no bean " + type.getName() + " in " + descriptor.beans().stream().map(BeanInfo::id).toList()));
    }

    private static PropertyInfo property(BeanInfo bean, String name) {
        return bean.properties().stream().filter(p -> p.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no property " + name + " on " + bean.id()));
    }

    private static ExecutableInfo executable(BeanInfo bean, String key) {
        return bean.executables().stream().filter(e -> e.key().equals(key)).findFirst()
                .orElseThrow(() -> new AssertionError("no executable " + key + " on " + bean.id()));
    }

    private static BeanRule rule(List<BeanRule> rules, String name) {
        return rules.stream().filter(r -> r.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no rule " + name + " in " + rules.stream().map(BeanRule::name).toList()));
    }

    private static List<Problem> problems(String code, String artifact) {
        return descriptor.problems().stream().filter(p -> p.code().equals(code) && artifact.equals(p.artifact())).toList();
    }

    private static List<Reference> references(String from, ReferenceType type) {
        return descriptor.references().stream().filter(r -> r.from().equals(from) && r.type() == type).toList();
    }

    @Test
    void beansAreSortedByIdAndTheRulesSectionIsUntouched() {
        assertEquals(List.of(Address.class, Broken.class, Customer.class, Line.class, Order.class, Plain.class, Priced.class,
                Shape.class, Shape.Circle.class, Staged.class).stream().map(Class::getName).toList(),
                descriptor.beans().stream().map(BeanInfo::id).toList());

        Descriptor withoutBeans = DescriptorBuilder.of(fixture.registry()).applicationName("order-service")
                .analyzers(List.of(OrderFixture.stubAnalyzer())).build();
        assertNull(withoutBeans.beans(), "absent, not empty, when no source is configured (FR-83)");
        assertEquals(withoutBeans.artifacts(), descriptor.artifacts(), "a bean's rules never become artifacts");
        assertEquals(withoutBeans.packages(), descriptor.packages());
        assertEquals(withoutBeans.bindings(), descriptor.bindings(), "bindings of beans are an M4 item");
        assertTrue(descriptor.references().containsAll(withoutBeans.references()));
    }

    @Test
    void discoveryAndKinds() {
        assertEquals(Discovery.SCAN, bean(Order.class).discovery());
        assertEquals(Discovery.CONFIGURED, bean(Plain.class).discovery());
        assertEquals(BeanKind.CLASS, bean(Order.class).kind());
        assertEquals(BeanKind.RECORD, bean(Line.class).kind());
        assertEquals(BeanKind.INTERFACE, bean(Priced.class).kind());
        assertEquals(BeanKind.ABSTRACT_CLASS, bean(Shape.class).kind());
        assertEquals(PACKAGE, bean(Order.class).packageId());
        assertEquals("Circle", bean(Shape.Circle.class).name());
        assertEquals("orderValidationFlow", bean(Order.class).flow().name());
        assertEquals(SourceType.JAVA, bean(Order.class).source().type());
        assertEquals(Order.class.getName(), bean(Order.class).source().className());
    }

    @Test
    void cascadeClosureAddsWhatTheScanDidNotName() {
        BeanValidator validator = BeanValidator.builder().registry(fixture.registry()).build();
        Descriptor fromOrder = DescriptorBuilder.of(fixture.registry())
                .beans(Beans.of(validator).classes(List.of(Order.class)).validated(false).build())
                .build();

        assertEquals(List.of(Address.class, Customer.class, Line.class, Order.class).stream().map(Class::getName).toList(),
                fromOrder.beans().stream().map(BeanInfo::id).toList(), "Customer and Line through Order, Address through Customer");
        assertEquals(Discovery.CONFIGURED, fromOrder.beans().get(3).discovery());
        assertEquals(Discovery.CASCADE, fromOrder.beans().get(0).discovery());
        assertEquals(Discovery.CASCADE, fromOrder.beans().get(2).discovery(), "the element type of a cascaded list");
    }

    @Test
    void classLevelDeclarations() {
        BeanInfo order = bean(Order.class);
        assertTrue(order.materialized());
        assertTrue(order.constraints().isEmpty());

        assertEquals(1, order.validations().size());
        assertEquals(List.of("minTotalRule"), order.validations().get(0).rules());
        assertEquals("IMPLICIT", order.validations().get(0).applyTo());
        assertEquals("minTotalRule", order.validations().get(0).targets().get(0).artifactId());

        assertEquals(1, order.expressions().size());
        assertEquals(ExpressionKind.SCRIPT, order.expressions().get(0).expression().kind());
        assertEquals("java", order.expressions().get(0).expression().language());
        assertEquals("ctx.total != null", order.expressions().get(0).expression().text());
        assertEquals(List.of("total"), order.expressions().get(0).expression().reads());
        assertEquals("order.total.present", order.expressions().get(0).errorCode());
        assertEquals(Order.class.getName() + "/rules/Order_expr", order.expressions().get(0).ruleId());

        assertEquals(2, order.binds().size());
        assertEquals(BindKind.LITERAL, order.binds().get(0).kind());
        assertEquals("500", order.binds().get(0).literal());
        assertEquals("java.lang.Integer", order.binds().get(0).type());
        assertNull(order.binds().get(0).applyTo(), "applyTo is an executable's");
        assertNull(order.binds().get(0).declaredOn(), "declared on the bean itself");
        assertEquals(BindKind.EXPRESSION, order.binds().get(1).kind());
        assertEquals("ctx.limit * 2", order.binds().get(1).expression().text());
        assertEquals(List.of("limit"), order.binds().get(1).expression().reads());

        assertEquals(List.of("Basic", "Default"), bean(Staged.class).groupSequence(), "the class itself stands for Default");
        assertNull(order.groupSequence(), "absent unless redefined");
    }

    @Test
    void propertiesAndTheirConstraints() {
        BeanInfo order = bean(Order.class);
        assertEquals(List.of("total", "code", "customer", "lines", "quantities", "priority", "price", "itemCount"),
                order.properties().stream().map(PropertyInfo::name).toList(), "declaration order, the interface's getter after the class's own");

        PropertyInfo total = property(order, "total");
        assertEquals(DeclaredOn.FIELD, total.declaredOn());
        assertNull(total.declaringClass(), "declared on the bean itself");
        assertEquals("java.math.BigDecimal", total.type());
        assertEquals(2, total.constraints().size());
        ConstraintInfo decimalMin = total.constraints().get(1);
        assertEquals("jakarta.validation.constraints.DecimalMin", decimalMin.annotation());
        assertEquals("decimalMin", decimalMin.name());
        assertEquals("0.0", decimalMin.attributes().get("value"));
        assertEquals(Boolean.TRUE, decimalMin.attributes().get("inclusive"));
        assertFalse(decimalMin.attributes().containsKey("message"), "message, groups and payload are not attributes");
        assertEquals("is at least 0.0", decimalMin.plain());
        assertEquals("{jakarta.validation.constraints.DecimalMin.message}", decimalMin.message());
        assertEquals("ERROR", decimalMin.severity());
        assertEquals(Order.class.getName() + "/rules/total_decimalMin", decimalMin.ruleId());

        PropertyInfo code = property(order, "code");
        assertEquals(5L, code.constraints().get(0).attributes().get("max"), "integers are longs, as read back");
        assertEquals(List.of("emailRequired", "prefixRule"), code.validations().get(0).rules());
        assertEquals("emailRequired", code.validations().get(0).targets().get(0).artifactId());
        assertNull(code.validations().get(0).targets().get(1).artifactId(), "nothing registered as prefixRule");

        PropertyInfo priority = property(order, "priority");
        assertEquals("WARNING", priority.expressions().get(0).severity());
        assertEquals(List.of("Advanced"), priority.expressions().get(0).groups());
        assertEquals("priority is out of range", priority.expressions().get(0).message());
        assertTrue(priority.expressions().get(0).skipNull());

        PropertyInfo price = property(order, "price");
        assertEquals(DeclaredOn.GETTER, price.declaredOn());
        assertEquals(Priced.class.getName(), price.declaringClass(), "inherited from the interface");
        assertEquals("positiveOrZero", price.constraints().get(0).name());

        assertEquals(DeclaredOn.GETTER, property(order, "itemCount").declaredOn());
        assertEquals(DeclaredOn.RECORD_COMPONENT, property(bean(Line.class), "sku").declaredOn());
        assertEquals(Shape.class.getName(), property(bean(Shape.Circle.class), "name").declaringClass());
    }

    @Test
    void cascadesAndContainerElements() {
        BeanInfo order = bean(Order.class);

        PropertyInfo customer = property(order, "customer");
        assertEquals(Customer.class.getName(), customer.cascade().beanId());
        assertEquals(Customer.class.getName(), customer.cascade().targetType());
        assertEquals("Default", customer.cascade().groupConversions().get(0).from());
        assertEquals("Basic", customer.cascade().groupConversions().get(0).to());

        PropertyInfo lines = property(order, "lines");
        assertEquals(Line.class.getName(), lines.cascade().beanId(), "@Valid on the list cascades into its elements");
        assertEquals("java.util.List<" + Line.class.getName() + ">", lines.cascade().targetType());
        ContainerElementInfo element = lines.containerElements().get(0);
        assertEquals("List<0>", element.name());
        assertEquals("java.util.List", element.containerType());
        assertEquals(0, element.typeArgumentIndex());
        assertEquals(Line.class.getName(), element.type());
        assertEquals(Order.class.getName() + "/rules/lines_List0_notNull", element.constraints().get(0).ruleId());
        assertNull(element.cascade());

        PropertyInfo quantities = property(order, "quantities");
        assertNull(quantities.cascade());
        assertEquals(List.of("Map<0>", "Map<1>"), quantities.containerElements().stream().map(ContainerElementInfo::name).toList());
        assertEquals("positive", quantities.containerElements().get(1).constraints().get(0).name());

        assertTrue(property(bean(Customer.class), "address").cascade().groupConversions().isEmpty());
        List<Reference> cascades = references(Order.class.getName(), ReferenceType.CASCADES);
        assertEquals(List.of("executables.order_merge.parameters[0]", "properties.customer", "properties.lines"),
                cascades.stream().map(Reference::path).toList());
        assertEquals(Resolution.DIRECT, cascades.get(0).resolution());
        assertEquals(Order.class.getName(), cascades.get(0).to(), "merge cascades into another Order");
    }

    @Test
    void executables() {
        BeanInfo order = bean(Order.class);
        assertEquals(List.of("order_Order", "order_add", "order_getItemCount", "order_getPrice", "order_merge"),
                order.executables().stream().map(ExecutableInfo::key).toList(), "sorted by key");

        ExecutableInfo constructor = executable(order, "order_Order");
        assertEquals(ExecutableKind.CONSTRUCTOR, constructor.kind());
        assertEquals("Order(java.lang.String)", constructor.signature());
        assertEquals("code", constructor.parameters().get(0).name());
        assertEquals("notBlank", constructor.parameters().get(0).constraints().get(0).name());
        assertEquals("order_OrderParametersFlow", constructor.parametersFlow().name());
        assertNull(constructor.returnValueFlow());
        assertNull(constructor.returnValue(), "nothing declared for the constructed object");
        assertNull(constructor.crossParameter());

        ExecutableInfo merge = executable(order, "order_merge");
        assertEquals(ExecutableKind.METHOD, merge.kind());
        assertEquals("merge(" + Order.class.getName() + ", int)", merge.signature());
        assertEquals(Order.class.getName(), merge.parameters().get(0).cascade().beanId());
        assertEquals("min", merge.parameters().get(1).constraints().get(0).name());
        assertEquals(1L, merge.parameters().get(1).constraints().get(0).attributes().get("value"));
        assertEquals("notNull", merge.returnValue().constraints().get(0).name());
        assertEquals(Order.class.getName(), merge.returnValue().type());
        assertEquals("order_mergeParametersFlow", merge.parametersFlow().name());
        assertEquals("order_mergeReturnValueFlow", merge.returnValueFlow().name());

        ExecutableInfo add = executable(order, "order_add");
        assertNull(add.returnValue(), "void");
        assertEquals("order.add.quantity", add.crossParameter().expressions().get(0).errorCode());
        assertEquals("PARAMETERS", add.crossParameter().expressions().get(0).applyTo());
        assertEquals(List.of("quantity"), add.crossParameter().expressions().get(0).expression().reads());
        assertEquals("maxQuantity", add.binds().get(0).name());
        assertEquals("IMPLICIT", add.binds().get(0).applyTo());

        ExecutableInfo getter = executable(order, "order_getPrice");
        assertEquals(ExecutableKind.GETTER, getter.kind());
        assertEquals("positiveOrZero", getter.returnValue().constraints().get(0).name());
    }

    @Test
    void materializedRulesStayWithTheBean() {
        BeanInfo order = bean(Order.class);
        BeanRule decimalMin = rule(order.rules(), "total_decimalMin");
        assertEquals(Order.class.getName() + "/rules/total_decimalMin", decimalMin.id());
        assertEquals(PlacementKind.PROPERTY, decimalMin.placement().kind());
        assertEquals("total", decimalMin.placement().name());
        assertNull(decimalMin.placement().index());
        assertEquals(ArtifactKind.PREDEFINED_VALIDATOR, decimalMin.kind());
        assertEquals("decimalMin", decimalMin.validation().validator());
        assertEquals(ValueSource.BINDING, decimalMin.validation().valueSource().kind());
        assertEquals("total", decimalMin.validation().valueSource().name());
        assertEquals("0.0", decimalMin.validation().settings().get("min"));
        assertNull(decimalMin.rule().given(), "the library's plumbing is not shown");
        assertTrue(decimalMin.parameters().isEmpty());

        BeanRule expr = rule(order.rules(), "Order_expr");
        assertEquals(PlacementKind.BEAN, expr.placement().kind());
        assertEquals(ArtifactKind.JAVA_BUILDER, expr.kind());
        assertEquals(ExpressionKind.SCRIPT, expr.rule().given().kind());
        assertEquals("ctx.total != null", expr.rule().given().text());
        assertNull(expr.rule().preCondition(), "the null-skip precondition is the library's");
        assertEquals("order.total.present", expr.validation().errorCode());

        BeanRule parameter = rule(executable(order, "order_merge").rules(), "priority_min");
        assertEquals(Order.class.getName() + "/order_merge/rules/priority_min", parameter.id());
        assertEquals(PlacementKind.PARAMETER, parameter.placement().kind());
        assertEquals(1, parameter.placement().index());
        assertEquals(PlacementKind.RETURN_VALUE, rule(executable(order, "order_merge").rules(), "returnValue_notNull").placement().kind());
        assertEquals(PlacementKind.CROSS_PARAMETER, rule(executable(order, "order_add").rules(), "add_expr").placement().kind());

        BeanInfo customer = bean(Customer.class);
        ConstraintInfo strict = property(customer, "code").constraints().get(0);
        assertTrue(strict.reportAsSingleViolation());
        assertEquals("@Codes.StrictCode", strict.plain(), "unknown constraints read as their annotation");
        assertEquals(Customer.class.getName() + "/rules/code_strictCode", strict.ruleId());
        assertEquals(List.of("notNull", "size"), strict.composing().stream().map(ConstraintInfo::name).toList());
        assertEquals(Customer.class.getName() + "/rules/code_strictCode_size", strict.composing().get(1).ruleId());
        BeanRule host = rule(customer.rules(), "code_strictCode");
        assertEquals(ArtifactKind.JAVA_BUILDER, host.kind());
        assertNull(host.rule().given());
        assertEquals(List.of("code_strictCode_notNull", "code_strictCode_size"), host.hosted().stream().map(BeanRule::name).toList());
        assertEquals(ArtifactKind.PREDEFINED_VALIDATOR, host.hosted().get(0).kind());

        ConstraintInfo valid = property(customer, "ref").constraints().get(0);
        assertFalse(valid.reportAsSingleViolation());
        assertNull(valid.ruleId(), "flattened: only its parts become rules");
        assertEquals(Customer.class.getName() + "/rules/ref_validCode_notNull", valid.composing().get(0).ruleId());
        assertNotNull(rule(customer.rules(), "ref_validCode_size"));
    }

    @Test
    void referencesAndProblems() {
        List<Reference> runs = references(Order.class.getName(), ReferenceType.RUNS);
        assertEquals(List.of("properties.code.validations[0]", "validations[0]"), runs.stream().map(Reference::path).toList());
        assertEquals("emailRequired", runs.get(0).to());
        assertEquals(Resolution.BY_NAME, runs.get(0).resolution());

        List<Problem> unresolved = problems(ProblemCodes.UNRESOLVED_TARGET, Order.class.getName());
        assertEquals(1, unresolved.size());
        assertEquals("properties.code.validations[0]", unresolved.get(0).path());
        assertEquals(ProblemSeverity.ERROR, unresolved.get(0).severity());
        assertTrue(unresolved.get(0).message().contains("prefixRule"));

        assertTrue(problems(ProblemCodes.UNUSED_RULE, "minAge").isEmpty(), "used by Staged's @Validate");
        assertFalse(problems(ProblemCodes.UNUSED_RULE, "customValidation").isEmpty());

        BeanInfo broken = bean(Broken.class);
        assertFalse(broken.materialized());
        assertTrue(broken.rules().isEmpty());
        assertEquals("notNull", property(broken, "value").constraints().get(0).name(), "shown from its declarations");
        assertNull(property(broken, "value").constraints().get(0).ruleId());
        assertEquals("abc", broken.binds().get(0).literal());
        List<Problem> notMaterialized = problems(ProblemCodes.BEAN_NOT_MATERIALIZED, Broken.class.getName());
        assertEquals(1, notMaterialized.size());
        assertTrue(notMaterialized.get(0).message().contains("BindDeclarationException"), notMaterialized.get(0).message());

        BeanInfo plain = bean(Plain.class);
        assertTrue(plain.materialized());
        assertEquals(1, plain.properties().size(), "every property the library binds is listed, constrained or not");
        assertTrue(property(plain, "nothing").constraints().isEmpty());
        assertTrue(plain.rules().isEmpty());
        List<Problem> undescribable = problems(ProblemCodes.UNDESCRIBABLE, Plain.class.getName());
        assertEquals(ProblemSeverity.WARNING, undescribable.get(0).severity());
        assertTrue(undescribable.get(0).message().contains("declares no constraint"));
    }

    @Test
    void theValidatorMustBeTheLibrarys() {
        BeanSource wrong = new BeanSource() {
            @Override
            public java.util.Map<Class<?>, Discovery> classes() {
                return java.util.Map.of();
            }

            @Override
            public Object validator() {
                return "not a validator";
            }
        };
        assertThrows(IllegalArgumentException.class, () -> DescriptorBuilder.of(fixture.registry()).beans(wrong).build());
    }
}
