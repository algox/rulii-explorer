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
package org.rulii.explorer.boot;

import org.junit.jupiter.api.Test;
import org.rulii.explorer.descriptor.Artifact;
import org.rulii.explorer.descriptor.Descriptor;
import org.rulii.explorer.descriptor.DescriptorJson;
import org.rulii.explorer.descriptor.Placeholder;
import org.rulii.model.condition.Condition;
import org.rulii.rule.Rule;
import org.rulii.script.Script;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code rulii.explorer.placeholders.show-values=always}: the descriptor carries the values the
 * scripts compiled with, except for the excluded keys and anything the application's own
 * {@link SanitizingFunction} beans would mask.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "rulii.explorer.placeholders.show-values=always",
        "rulii.explorer.placeholders.additional-exclude=pricing.vipDiscount",
        "order.minTotal=150",
        "vendor.secretLimit=987654",
        "pricing.vipDiscount=0.25",
        "risk.band=777"})
@Import(PlaceholderValuesTest.Rules.class)

class PlaceholderValuesTest {

    static final String SOURCE = "#ctx.total >= ${order.minTotal:100} && #ctx.total < ${vendor.secretLimit:1000}"
            + " && #ctx.discount <= ${pricing.vipDiscount:0.10} && #ctx.band != ${risk.band:0} && #ctx.age >= ${customer.minAge:18}";

    @TestConfiguration
    static class Rules {

        @Bean
        public Rule thresholdRule() {
            return Rule.builder()
                    .name("thresholdRule", "Total within the configured band.")
                    .given(Condition.builder().build(Script.builder().build("el", SOURCE)))
                    .build();
        }

        /** The application's own sanitizer, as it would configure for /actuator/env. */
        @Bean
        public SanitizingFunction riskSanitizer() {
            return SanitizingFunction.sanitizeValue().ifKeyEquals("risk.band");
        }
    }

    @Autowired
    private DescriptorService service;

    @Test
    void valuesShownExceptExcludedAndSanitized() {
        Descriptor descriptor = service.snapshot().descriptor();
        assertTrue(descriptor.application().placeholderValues());

        Artifact rule = descriptor.artifacts().stream().filter(a -> a.id().equals("thresholdRule")).findFirst().orElseThrow();
        assertEquals(SOURCE, rule.rule().given().text(), "the script stays as written");
        List<Placeholder> placeholders = rule.rule().given().placeholders();
        assertEquals(List.of(
                new Placeholder("order.minTotal", "100", "150", null),
                new Placeholder("vendor.secretLimit", "1000", null, Boolean.TRUE),
                new Placeholder("pricing.vipDiscount", "0.10", null, Boolean.TRUE),
                new Placeholder("risk.band", "0", null, Boolean.TRUE),
                new Placeholder("customer.minAge", "18", "18", null)), placeholders);

        String json = DescriptorJson.toJson(descriptor);
        assertFalse(json.contains("987654"), "default exclude *secret*");
        assertFalse(json.contains("0.25"), "additional-exclude");
        assertFalse(json.contains("777"), "the application's sanitizing function");
    }
}
