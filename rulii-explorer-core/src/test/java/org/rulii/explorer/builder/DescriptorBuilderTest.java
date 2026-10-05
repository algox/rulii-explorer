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
import org.rulii.explorer.descriptor.Artifact;
import org.rulii.explorer.descriptor.ArtifactKind;
import org.rulii.explorer.descriptor.ArtifactType;
import org.rulii.explorer.descriptor.BindingUsage;
import org.rulii.explorer.descriptor.Command;
import org.rulii.explorer.descriptor.CommandType;
import org.rulii.explorer.descriptor.Descriptor;
import org.rulii.explorer.descriptor.ExpressionKind;
import org.rulii.explorer.descriptor.Problem;
import org.rulii.explorer.descriptor.ProblemSeverity;
import org.rulii.explorer.descriptor.Reference;
import org.rulii.explorer.descriptor.ReferenceType;
import org.rulii.explorer.descriptor.Resolution;
import org.rulii.explorer.descriptor.SourceType;
import org.rulii.explorer.descriptor.TargetKind;
import org.rulii.explorer.descriptor.ValueSource;
import org.rulii.explorer.fixture.MapRegistry;
import org.rulii.explorer.fixture.OrderFixture;
import org.rulii.explorer.fixture.StockAvailableRule;
import org.rulii.explorer.problem.ProblemCodes;
import org.rulii.model.UnrulyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The descriptor built from the Java fixture: every artifact kind, command type, reference,
 * binding and problem code.
 */
class DescriptorBuilderTest {

    private static OrderFixture fixture;
    private static Descriptor descriptor;

    @BeforeAll
    static void build() {
        fixture = new OrderFixture();
        descriptor = DescriptorBuilder.of(fixture.registry())
                .applicationName("order-service")
                .analyzers(List.of(OrderFixture.stubAnalyzer()))
                .build();
    }

    private static Artifact artifact(String id) {
        return descriptor.artifacts().stream().filter(a -> a.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("no artifact " + id + " in " + descriptor.artifacts().stream().map(Artifact::id).toList()));
    }

    private static List<Problem> problems(String code) {
        return descriptor.problems().stream().filter(p -> p.code().equals(code)).toList();
    }

    @Test
    void headerAndOrdering() {
        assertEquals("1.0", descriptor.descriptorVersion());
        assertEquals("order-service", descriptor.application().name());
        assertNotNull(descriptor.application().ruliiVersion());
        assertNotNull(descriptor.application().explorerVersion());

        List<String> ids = descriptor.artifacts().stream().map(Artifact::id).toList();
        assertEquals(List.of("anotherMinTotalRule", "customValidation", "emailRequired", "minAge", "minTotalRule",
                "orderProcessingFlow/commands[11]/target", "orderValidationRules/members[2]", "scriptRule",
                "stockAvailableRule", "orderValidationRules", "contextFlow", "orderProcessingFlow"), ids,
                "rules, then rule sets, then flows; ids sorted within a type");

        assertEquals(List.of("org.rulii.explorer.fixture"), descriptor.packages().stream().map(p -> p.id()).toList());
    }

    @Test
    void classBasedRule() {
        Artifact rule = artifact("stockAvailableRule");
        assertEquals("StockAvailableRule", rule.name());
        assertEquals(ArtifactType.RULE, rule.type());
        assertEquals(ArtifactKind.RULE_CLASS, rule.kind());
        assertTrue(rule.registered());
        assertEquals(StockAvailableRule.class.getName(), rule.className());
        assertEquals("org.rulii.explorer.fixture", rule.packageId());
        assertNull(rule.description());
        assertEquals(SourceType.JAVA, rule.source().type());
        assertEquals(StockAvailableRule.class.getName(), rule.source().className());

        assertEquals(ExpressionKind.COMPILED, rule.rule().given().kind());
        assertEquals("boolean when(Integer quantity, Integer stock)", rule.rule().given().signature());
        assertEquals(List.of("quantity", "stock"), rule.rule().given().reads());
        assertEquals(1, rule.rule().then().size());
        assertEquals("void then(Integer quantity)", rule.rule().then().get(0).signature());

        assertEquals(List.of("quantity", "stock"), rule.parameters().stream().map(p -> p.name()).toList());
        assertEquals("java.lang.Integer", rule.parameters().get(0).type());
        assertTrue(rule.parameters().get(0).required());
    }

    @Test
    void lambdaRuleRecordsTheBuilderCallSiteAndSignatures() {
        Artifact rule = artifact("minTotalRule");
        assertEquals("MinTotalRule", rule.name(), "own name differs from the registry name");
        assertEquals(ArtifactKind.JAVA_BUILDER, rule.kind());
        assertEquals("Order total must meet the minimum.", rule.description());
        assertEquals(OrderFixture.class.getName(), rule.source().className());
        assertNull(rule.className(), "only class-based rules carry a rule class");
        assertEquals("boolean test(Integer total)", rule.rule().given().signature());
        assertEquals("void run(Integer total)", rule.rule().then().get(0).signature());
        assertNull(rule.rule().preCondition());
        assertNull(rule.rule().otherwise());
        assertNull(rule.validation());
    }

    @Test
    void scriptRuleKeepsItsTextAndIsAnalysed() {
        Artifact rule = artifact("scriptRule");
        assertEquals(ExpressionKind.SCRIPT, rule.rule().given().kind());
        assertEquals(OrderFixture.SCRIPT_LANGUAGE, rule.rule().given().language());
        assertEquals("ctx.total >= 100", rule.rule().given().text());
        assertNotNull(rule.rule().given().plain(), "the stub analyzer handles the language");
        assertFalse(rule.rule().given().plain().complete());
        assertEquals("raw", rule.rule().given().plain().tokens().get(0).t());
        assertEquals(List.of("total"), rule.rule().given().reads());
        assertTrue(rule.parameters().isEmpty(), "a script has no declared parameters");
    }

    @Test
    void predefinedValidators() {
        Artifact notNull = artifact("emailRequired");
        assertEquals(ArtifactKind.PREDEFINED_VALIDATOR, notNull.kind());
        assertEquals("notNull", notNull.validation().validator());
        assertEquals("customer.email.missing", notNull.validation().errorCode());
        assertEquals("ERROR", notNull.validation().severity());
        assertEquals(ValueSource.BINDING, notNull.validation().valueSource().kind());
        assertEquals("email", notNull.validation().valueSource().name());
        assertEquals(Boolean.TRUE, notNull.validation().failOnNull());
        assertTrue(notNull.validation().settings().isEmpty(), notNull.validation().settings().toString());
        assertEquals(OrderFixture.class.getName(), notNull.source().className(), "the caller, not rulii's validator class");

        Artifact min = artifact("minAge");
        assertEquals("min", min.validation().validator());
        assertEquals(18L, min.validation().settings().get("min"));
        assertEquals("{0} must be at least 18", min.validation().errorMessage());
        assertNull(min.rule().given(), "a predefined validator is described by its validation section, not rulii's methods");
        assertTrue(min.parameters().isEmpty(), "no rulii plumbing parameters");
    }

    @Test
    void customValidationRule() {
        Artifact custom = artifact("customValidation");
        assertEquals(ArtifactKind.JAVA_BUILDER, custom.kind());
        assertNull(custom.validation().validator());
        assertEquals("order.total.invalid", custom.validation().errorCode());
        assertEquals("WARNING", custom.validation().severity());
        assertNull(custom.validation().valueSource());
        assertEquals("boolean test(Integer total)", custom.rule().given().signature());
    }

    @Test
    void ruleSetHidesRuliiPlumbingAndLinksMembers() {
        Artifact ruleSet = artifact("orderValidationRules");
        assertEquals(ArtifactType.RULESET, ruleSet.type());
        assertEquals(ArtifactKind.JAVA_BUILDER, ruleSet.kind());
        assertTrue(ruleSet.ruleSet().validating());
        assertEquals(List.of("minTotalRule", "emailRequired", "orderValidationRules/members[2]"), ruleSet.ruleSet().members());
        assertEquals(List.of("order", "customer"), ruleSet.parameters().stream().map(p -> p.name()).toList(),
                "the ruleViolations parameter validating() adds is not shown");
        assertEquals("The order", ruleSet.parameters().get(0).description());
        assertFalse(ruleSet.parameters().get(1).required());

        assertEquals("boolean test(Object order)", ruleSet.ruleSet().preCondition().signature());
        assertEquals("boolean test(Integer violationCount)", ruleSet.ruleSet().stopCondition().signature());
        assertNull(ruleSet.ruleSet().initializer());
        assertNull(ruleSet.ruleSet().finalizer(), "the validating check is rulii's, not the application's");
        assertNull(ruleSet.ruleSet().resultExtractor(), "the default extractor is rulii's");
        assertNull(ruleSet.ruleSet().errorHandler(), "the default handler is rulii's");

        Artifact inline = artifact("orderValidationRules/members[2]");
        assertFalse(inline.registered());
        assertEquals("InlineRule", inline.name());

        List<Reference> contains = descriptor.references().stream()
                .filter(r -> r.from().equals("orderValidationRules")).toList();
        assertEquals(3, contains.size());
        assertEquals(ReferenceType.CONTAINS, contains.get(0).type());
        assertEquals("members[0]", contains.get(0).path());
        assertEquals("minTotalRule", contains.get(0).to());
    }

    @Test
    void ruleFlowCommandTree() {
        Artifact flow = artifact("orderProcessingFlow");
        assertEquals(ArtifactType.RULEFLOW, flow.type());
        assertNull(flow.ruleFlow().context());
        assertEquals("java.lang.Boolean", flow.ruleFlow().resultType());
        assertEquals("Boolean apply(Boolean approved)", flow.ruleFlow().returning().signature());
        assertNotNull(flow.ruleFlow().finalizer());
        assertEquals("java.lang.Exception", flow.ruleFlow().globalHandler().exceptionType());
        assertEquals(CommandType.EXECUTE, flow.ruleFlow().globalHandler().body().get(0).type());

        List<Command> commands = flow.ruleFlow().commands();
        assertEquals(List.of(CommandType.BIND, CommandType.RUN, CommandType.WHEN, CommandType.ASYNC_RUN, CommandType.AWAIT,
                CommandType.RUN, CommandType.RUN, CommandType.RUN, CommandType.FOR_EACH, CommandType.SCOPE, CommandType.APPLY,
                CommandType.RUN, CommandType.CUSTOM), commands.stream().map(Command::type).toList());

        Command bind = commands.get(0);
        assertEquals("literal", bind.bind().kind());
        assertEquals("reviewQueue", bind.bind().names().get(0).name());
        assertEquals("java.lang.String", bind.bind().names().get(0).type());

        Command run = commands.get(1);
        assertEquals(TargetKind.INSTANCE, run.target().kind());
        assertEquals("orderValidationRules", run.target().id());
        assertEquals(Resolution.DIRECT, run.target().resolution());
        assertEquals("validation", run.as());

        Command when = commands.get(2);
        assertEquals("boolean test(Boolean approved)", when.condition().signature());
        assertEquals(CommandType.EXIT, when.then().get(0).type());
        assertNull(when.then().get(0).expression());
        assertTrue(when.otherwise().isEmpty());

        Command async = commands.get(3);
        assertEquals("scriptRule", async.target().id());
        assertEquals("fraudCheck", async.as());
        assertEquals("immutable", async.mode());
        assertEquals("java.lang.IllegalStateException", async.handler().exceptionType());
        assertEquals(CommandType.BIND, async.handler().body().get(0).type());

        Command await = commands.get(4);
        assertEquals("one", await.awaitKind());
        assertEquals(List.of("fraudCheck"), await.names());
        assertEquals("PT5S", await.timeout());

        Command byNameMismatch = commands.get(5);
        assertEquals(TargetKind.BY_NAME, byNameMismatch.target().kind());
        assertEquals("MinTotalRule", byNameMismatch.target().name());
        assertEquals(Resolution.UNRESOLVED, byNameMismatch.target().resolution());

        Command byClass = commands.get(7);
        assertEquals(TargetKind.BY_CLASS, byClass.target().kind());
        assertEquals("stockAvailableRule", byClass.target().id());
        assertEquals(Resolution.BY_CLASS, byClass.target().resolution());

        Command forEach = commands.get(8);
        assertEquals("item", forEach.item());
        assertNotNull(forEach.source());
        assertNull(forEach.stop());
        assertEquals(CommandType.EXECUTE, forEach.body().get(0).type());

        assertEquals("tmp", commands.get(9).name());
        assertEquals("doubled", commands.get(10).as());
        assertEquals("orderProcessingFlow/commands[11]/target", commands.get(11).target().id());
        assertEquals(OrderFixture.AuditCommand.class.getName(), commands.get(12).className());

        Artifact inlineTarget = artifact("orderProcessingFlow/commands[11]/target");
        assertFalse(inlineTarget.registered());
        assertEquals("InlineTarget", inlineTarget.name());

        assertEquals("flowContext", artifact("contextFlow").ruleFlow().context());
    }

    @Test
    void referencesOnlyIncludeWhatResolves() {
        List<Reference> runs = descriptor.references().stream().filter(r -> r.from().equals("orderProcessingFlow")).toList();
        assertEquals(List.of("commands[1]", "commands[3]", "commands[7]", "commands[11]"), runs.stream().map(Reference::path).toList(),
                "paths sort naturally, not lexically");
        assertEquals(List.of("orderValidationRules", "scriptRule", "stockAvailableRule", "orderProcessingFlow/commands[11]/target"),
                runs.stream().map(Reference::to).toList());
        assertTrue(runs.get(1).async());
        assertEquals(Resolution.BY_CLASS, runs.get(2).resolution());
        assertTrue(descriptor.references().stream().noneMatch(r -> r.to() == null));
    }

    @Test
    void problems() {
        List<Problem> unresolved = problems(ProblemCodes.UNRESOLVED_TARGET);
        assertEquals(1, unresolved.size());
        assertEquals(ProblemSeverity.ERROR, unresolved.get(0).severity());
        assertEquals("commands[6]", unresolved.get(0).path());
        assertTrue(unresolved.get(0).message().contains("prefixRule"));

        List<Problem> mismatch = problems(ProblemCodes.NAME_MISMATCH_LOOKUP);
        assertEquals(1, mismatch.size());
        assertEquals("commands[5]", mismatch.get(0).path());
        assertTrue(mismatch.get(0).message().contains("anotherMinTotalRule") || mismatch.get(0).message().contains("minTotalRule"));

        assertEquals(List.of("anotherMinTotalRule", "minTotalRule"),
                problems(ProblemCodes.DUPLICATE_NAME).stream().map(Problem::artifact).toList());

        List<String> unused = problems(ProblemCodes.UNUSED_RULE).stream().map(Problem::artifact).toList();
        assertEquals(List.of("anotherMinTotalRule", "customValidation", "minAge"), unused);

        List<String> undescribed = problems(ProblemCodes.MISSING_DESCRIPTION).stream().map(Problem::artifact).toList();
        assertEquals(List.of("stockAvailableRule"), undescribed);

        assertEquals(ProblemSeverity.ERROR, descriptor.problems().get(0).severity(), "most severe first");
        assertEquals(ProblemSeverity.INFO, descriptor.problems().get(descriptor.problems().size() - 1).severity());
    }

    @Test
    void bindings() {
        BindingUsage total = binding("total");
        assertEquals(List.of("anotherMinTotalRule", "customValidation", "minTotalRule", "orderProcessingFlow",
                "orderValidationRules/members[2]", "scriptRule"), total.readBy());
        assertTrue(total.unknownWriters().contains("minTotalRule"), "its then action is compiled code");
        assertFalse(total.unknownWriters().contains("scriptRule"), "no actions at all");

        BindingUsage quantity = binding("quantity");
        assertEquals(List.of("stockAvailableRule"), quantity.readBy());
        assertEquals(List.of("stockAvailableRule"), quantity.unknownWriters());

        assertEquals(List.of("orderProcessingFlow"), binding("validation").writtenBy());
        assertEquals(List.of("orderProcessingFlow"), binding("reviewQueue").writtenBy());
        assertEquals(List.of("orderProcessingFlow"), binding("item").writtenBy());
        assertEquals(List.of("orderProcessingFlow", "orderValidationRules"), binding("order").readBy());
        assertEquals(List.of("emailRequired"), binding("email").readBy());
    }

    private static BindingUsage binding(String name) {
        return descriptor.bindings().stream().filter(b -> b.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no binding " + name + " in " + descriptor.bindings().stream().map(BindingUsage::name).toList()));
    }

    @Test
    void sourcesCanBeHidden() {
        Descriptor hidden = DescriptorBuilder.of(fixture.registry()).includeSources(false).build();
        assertTrue(hidden.artifacts().stream().allMatch(a -> a.source() == null && a.className() == null));
        assertEquals(descriptor.artifacts().size(), hidden.artifacts().size());
        assertEquals("org.rulii.explorer.fixture", hidden.artifacts().get(0).packageId(), "packages stay");
    }

    @Test
    void failuresNeverBreakTheBuild() {
        MapRegistry registry = fixture.registry().registerFailing("brokenBean", new UnrulyException("bean creation failed"));
        Descriptor withFailure = DescriptorBuilder.of(registry).build();

        List<Problem> undescribable = withFailure.problems().stream().filter(p -> p.code().equals(ProblemCodes.UNDESCRIBABLE)).toList();
        assertEquals(1, undescribable.size());
        assertEquals("brokenBean", undescribable.get(0).artifact());
        assertTrue(undescribable.get(0).message().contains("bean creation failed"));
        assertEquals(descriptor.artifacts().size(), withFailure.artifacts().size(), "everything else is still described");
    }
}
