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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.rulii.explorer.descriptor.Artifact;
import org.rulii.explorer.descriptor.Descriptor;
import org.rulii.explorer.descriptor.DescriptorJson;
import org.rulii.explorer.descriptor.Expression;
import org.rulii.explorer.descriptor.Placeholder;
import org.rulii.explorer.fixture.MapRegistry;
import org.rulii.explorer.fixture.OrderFixture;
import org.rulii.model.condition.Condition;
import org.rulii.rule.Rule;
import org.rulii.script.Script;
import org.rulii.script.ScriptProcessorManager;

import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Placeholder values end to end: a resolver installed on rulii's script manager (as rulii-spring
 * does), a rule compiled with it, and the descriptor built with and without a filter. The compiled
 * text itself never reaches the descriptor, only the values that passed the filter.
 *
 */
class PlaceholderValuesBuilderTest {

    private static final String SOURCE = "ctx.total >= ${order.minTotal:100} && ctx.total < ${vendor.secretLimit:1000}";

    private static MapRegistry registry;

    @BeforeAll
    static void compileWithAResolver() {
        ScriptProcessorManager.getInstance().setScriptTextResolver(text -> text
                .replace("${order.minTotal:100}", "150")
                .replace("${vendor.secretLimit:1000}", "987654"));
        Rule rule = Rule.builder()
                .name("ThresholdRule", "Total within the configured band.")
                .given(Condition.builder().build(Script.builder().build(OrderFixture.SCRIPT_LANGUAGE, SOURCE)))
                .build();
        registry = new MapRegistry().register("thresholdRule", rule);
    }

    @AfterAll
    static void resetResolver() {
        ScriptProcessorManager.getInstance().setScriptTextResolver(UnaryOperator.identity());
    }

    private static Expression given(Descriptor descriptor) {
        Artifact artifact = descriptor.artifacts().stream().filter(a -> a.id().equals("thresholdRule")).findFirst().orElseThrow();
        return artifact.rule().given();
    }

    @Test
    void offByDefault_keysAndDefaultsOnly() {
        Descriptor descriptor = DescriptorBuilder.of(registry).analyzers(List.of(OrderFixture.stubAnalyzer())).build();
        assertFalse(descriptor.application().placeholderValues());

        Expression given = given(descriptor);
        assertEquals(SOURCE, given.text(), "the text stays as written");
        assertEquals(List.of(Placeholder.of("order.minTotal", "100"), Placeholder.of("vendor.secretLimit", "1000")),
                given.placeholders());
        assertFalse(DescriptorJson.toJson(descriptor).contains("150"), "no value leaks when values are off");
    }

    @Test
    void onWithAFilter_valuesShownOrHidden() {
        Descriptor descriptor = DescriptorBuilder.of(registry)
                .analyzers(List.of(OrderFixture.stubAnalyzer()))
                .placeholderValues(PlaceholderFilter.excluding(PlaceholderFilter.DEFAULT_EXCLUDES))
                .build();
        assertTrue(descriptor.application().placeholderValues());

        Expression given = given(descriptor);
        assertEquals(SOURCE, given.text(), "the text stays as written even when values are shown");
        List<Placeholder> placeholders = given.placeholders();
        assertEquals(2, placeholders.size());
        assertEquals(new Placeholder("order.minTotal", "100", "150", null), placeholders.get(0));
        assertEquals(new Placeholder("vendor.secretLimit", "1000", null, Boolean.TRUE), placeholders.get(1));

        String json = DescriptorJson.toJson(descriptor);
        assertTrue(json.contains("\"value\": \"150\"") || json.contains("\"value\":\"150\""), json);
        assertFalse(json.contains("987654"), "a hidden value is nowhere in the descriptor");
    }

    @Test
    void showAll() {
        Descriptor descriptor = DescriptorBuilder.of(registry)
                .analyzers(List.of(OrderFixture.stubAnalyzer()))
                .placeholderValues(PlaceholderFilter.SHOW_ALL)
                .build();
        List<Placeholder> placeholders = given(descriptor).placeholders();
        assertEquals("150", placeholders.get(0).value());
        assertEquals("987654", placeholders.get(1).value());
        assertNull(placeholders.get(1).hidden());
    }

    @Test
    void scriptWithoutPlaceholdersHasNoList() {
        Descriptor descriptor = DescriptorBuilder.of(new OrderFixture().registry())
                .analyzers(List.of(OrderFixture.stubAnalyzer()))
                .placeholderValues(PlaceholderFilter.SHOW_ALL)
                .build();
        Artifact rule = descriptor.artifacts().stream().filter(a -> a.id().equals("scriptRule")).findFirst().orElseThrow();
        assertNull(rule.rule().given().placeholders());
    }
}
