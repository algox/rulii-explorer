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
package com.acme.order;

import org.junit.jupiter.api.Test;
import org.rulii.explorer.boot.RuliiDescriptors;
import org.rulii.explorer.descriptor.Artifact;
import org.rulii.explorer.descriptor.ArtifactKind;
import org.rulii.explorer.descriptor.ArtifactType;
import org.rulii.explorer.descriptor.Descriptor;
import org.rulii.explorer.descriptor.DescriptorJson;
import org.rulii.explorer.descriptor.Problem;
import org.rulii.explorer.descriptor.ProblemSeverity;
import org.rulii.explorer.problem.ProblemCodes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The order-service descriptor: the numbers from the design brief, the plain-English
 * translations of the XML rules, and the golden file the UI milestones build against. Rerun
 * with {@code -Dgolden.update=true} after an intended change and review the diff.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrderServiceDescriptorTest {

    private static final Path GOLDEN = Path.of("src/test/resources/golden/order-service.json");

    @Autowired
    private ApplicationContext context;

    private Descriptor descriptor() {
        return RuliiDescriptors.describe(context);
    }

    private static Artifact artifact(Descriptor descriptor, String id) {
        return descriptor.artifacts().stream().filter(a -> a.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("no artifact " + id + " in "
                        + descriptor.artifacts().stream().map(Artifact::id).toList()));
    }

    @Test
    void theNumbersFromTheBrief() {
        Descriptor descriptor = descriptor();
        assertEquals("order-service", descriptor.application().name());

        Map<ArtifactType, Long> byType = descriptor.artifacts().stream()
                .collect(Collectors.groupingBy(Artifact::type, Collectors.counting()));
        assertEquals(14L, byType.get(ArtifactType.RULE));
        assertEquals(3L, byType.get(ArtifactType.RULESET));
        assertEquals(2L, byType.get(ArtifactType.RULEFLOW));
        assertEquals(19, descriptor.artifacts().size());

        assertEquals(List.of("com.acme.order.config", "com.acme.order.rules", "rules/order", "rules/pricing"),
                descriptor.packages().stream().map(p -> p.id()).toList());

        Map<ArtifactKind, Long> ruleKinds = descriptor.artifacts().stream().filter(a -> a.type() == ArtifactType.RULE)
                .collect(Collectors.groupingBy(Artifact::kind, Collectors.counting()));
        assertEquals(8L, ruleKinds.get(ArtifactKind.XML_SCRIPT));
        assertEquals(2L, ruleKinds.get(ArtifactKind.PREDEFINED_VALIDATOR));
        assertEquals(2L, ruleKinds.get(ArtifactKind.RULE_CLASS));
        assertEquals(2L, ruleKinds.get(ArtifactKind.JAVA_BUILDER));
    }

    @Test
    void theProblemsFromTheBrief() {
        Map<ProblemSeverity, List<Problem>> bySeverity = descriptor().problems().stream()
                .collect(Collectors.groupingBy(Problem::severity));

        List<Problem> errors = bySeverity.get(ProblemSeverity.ERROR);
        assertEquals(1, errors.size());
        assertEquals(ProblemCodes.UNRESOLVED_TARGET, errors.get(0).code());
        assertEquals("nightlyRepriceFlow", errors.get(0).artifact());
        assertTrue(errors.get(0).message().contains("prefixRule"));

        List<Problem> warnings = bySeverity.get(ProblemSeverity.WARNING);
        assertEquals(1, warnings.size());
        assertEquals(ProblemCodes.NAME_MISMATCH_LOOKUP, warnings.get(0).code());
        assertTrue(warnings.get(0).message().contains("rangeCheckRule"), warnings.get(0).message());

        List<String> missing = bySeverity.get(ProblemSeverity.INFO).stream()
                .filter(p -> p.code().equals(ProblemCodes.MISSING_DESCRIPTION)).map(Problem::artifact).toList();
        assertEquals(List.of("ConsistentDatesRule", "StockAvailableRule"), missing);
    }

    @Test
    void xmlRulesAreTranslatedAndLocated() {
        Descriptor descriptor = descriptor();

        Artifact minTotal = artifact(descriptor, "MinTotalRule");
        assertEquals("rules/order", minTotal.packageId());
        assertEquals("classpath:rules/order/validation.xml", minTotal.source().resource());
        assertNotNull(minTotal.source().line());
        assertEquals("#ctx.order.total >= ${order.minTotal:100}", minTotal.rule().given().text());
        assertEquals("order total is at least order.minTotal (default 100)",
                minTotal.rule().given().plain().tokens().stream().map(t -> t.text()).collect(Collectors.joining(" ")));
        assertEquals(List.of("order.total"), minTotal.rule().given().reads());

        Artifact email = artifact(descriptor, "EmailFormatRule");
        assertEquals(ArtifactKind.PREDEFINED_VALIDATOR, email.kind());
        assertEquals("email", email.validation().validator());
        assertEquals("customer.email.invalid", email.validation().errorCode());
        assertEquals("#ctx.customer.email", email.validation().valueSource().expression().text());

        Artifact validation = artifact(descriptor, "orderValidationRules");
        assertTrue(validation.ruleSet().validating());
        assertEquals(8, validation.ruleSet().members().size());
        assertEquals("ConsistentDatesRule", validation.ruleSet().members().get(5), "class-scanned rules are members by bean name");

        Artifact fraud = artifact(descriptor, "fraudScoreRule");
        assertEquals("FraudScoreRule", fraud.name());
        assertEquals(ArtifactKind.JAVA_BUILDER, fraud.kind());
        assertEquals("com.acme.order.config.RiskConfig", fraud.source().className());
        assertEquals("fraudScoreRule", fraud.source().methodName());
        assertEquals("boolean test(Order order, Customer customer)", fraud.rule().given().signature());

        Artifact flow = artifact(descriptor, "orderProcessingFlow");
        assertEquals(7, flow.ruleFlow().commands().size());
        assertEquals("orderServices", flow.ruleFlow().commands().get(0).bind().label(), "bind ref keeps the bean name");
        assertEquals("fraudScoreRule", flow.ruleFlow().commands().get(3).target().id());
        assertEquals("immutable", flow.ruleFlow().commands().get(3).mode());
    }

    @Test
    void matchesTheGoldenDescriptor() throws IOException {
        String json = DescriptorJson.toJson(descriptor());

        if (Boolean.getBoolean("golden.update") || !Files.exists(GOLDEN)) {
            Files.createDirectories(GOLDEN.getParent());
            Files.writeString(GOLDEN, json, StandardCharsets.UTF_8);
            fail("Golden descriptor written to " + GOLDEN.toAbsolutePath() + "; review it and run again.");
        }

        String golden = Files.readString(GOLDEN, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertEquals(golden, json, "the order-service descriptor changed; rerun with -Dgolden.update=true if intended");
    }
}
