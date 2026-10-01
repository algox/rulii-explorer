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
package com.acme.order.scale;

import org.rulii.model.SourceDefinition;
import org.rulii.model.action.Action;
import org.rulii.model.condition.Condition;
import org.rulii.model.function.Function;
import org.rulii.rule.Rule;
import org.rulii.ruleflow.RuleFlow;
import org.rulii.ruleset.RuleSet;
import org.rulii.script.Script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.rulii.model.action.Actions.action;
import static org.rulii.model.condition.Conditions.condition;

/**
 * A synthetic application of any size for the scale tests (NFR-20, VQ-12): 5% rule flows, 15%
 * rule sets, 80% rules, the proportions of the M1 spike, spread over a dozen pseudo XML packages
 * so the whole-application graph has groups. Deterministic for a seed, so the numbers are
 * comparable between runs.
 *
 * <p>Every rule belongs to at least one rule set; every rule set is run by at least one flow;
 * flows bind, branch, loop, run work in the background and await it, and a few look artifacts up
 * by name, including names that do not exist, so the problems page has something to say.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ScaleGenerator {

    private static final String[] NOUNS = {"order", "customer", "item", "invoice", "shipment", "payment", "account", "cart", "coupon", "region"};
    private static final String[] PROPERTIES = {"total", "age", "tier", "price", "quantity", "balance", "limit", "score", "weight", "country"};
    private static final String[] WORDS = {"Order", "Customer", "Credit", "Shipping", "Fraud", "Stock", "Price", "Discount", "Email", "Phone",
            "Age", "Limit", "Region", "Tax", "Invoice", "Return", "Loyalty", "Catalog", "Delivery", "Payment"};
    private static final int PACKAGES = 12;

    private final Random random;
    private final int size;

    /** Everything generated, by bean name, in registration order. */
    private final Map<String, Object> beans = new LinkedHashMap<>();
    private final List<Rule> rules = new ArrayList<>();
    private final List<RuleSet<?>> ruleSets = new ArrayList<>();
    private final List<RuleFlow<?>> flows = new ArrayList<>();

    private ScaleGenerator(int size, long seed) {
        super();
        this.size = size;
        this.random = new Random(seed);
    }

    /**
     * @param size the number of artifacts to generate (at least 20).
     * @param seed the random seed.
     * @return bean name to artifact, in registration order.
     */
    public static Map<String, Object> generate(int size, long seed) {
        ScaleGenerator g = new ScaleGenerator(Math.max(20, size), seed);
        g.build();
        return g.beans;
    }

    /** The bean names and types {@link #generate} will produce for a size, without building anything. */
    public static Map<String, Class<?>> plan(int size) {
        int n = Math.max(20, size);
        int flowCount = Math.max(1, Math.round(n * 0.05f));
        int setCount = Math.max(1, Math.round(n * 0.15f));
        int ruleCount = n - flowCount - setCount;
        Map<String, Class<?>> plan = new LinkedHashMap<>();
        for (int i = 0; i < ruleCount; i++) plan.put("ScaleRule" + i, Rule.class);
        for (int i = 0; i < setCount; i++) plan.put("scaleRules" + i, RuleSet.class);
        for (int i = 0; i < flowCount; i++) plan.put("scaleFlow" + i, RuleFlow.class);
        return plan;
    }

    private void build() {
        int flowCount = Math.max(1, Math.round(size * 0.05f));
        int setCount = Math.max(1, Math.round(size * 0.15f));
        int ruleCount = size - flowCount - setCount;

        for (int i = 0; i < ruleCount; i++) rules.add(rule(i));
        for (int i = 0; i < setCount; i++) ruleSets.add(ruleSet(i, ruleCount));
        for (int i = 0; i < flowCount; i++) flows.add(flow(i, setCount, flowCount));

        for (int i = 0; i < rules.size(); i++) beans.put("ScaleRule" + i, rules.get(i));
        for (int i = 0; i < ruleSets.size(); i++) beans.put("scaleRules" + i, ruleSets.get(i));
        for (int i = 0; i < flows.size(); i++) beans.put("scaleFlow" + i, flows.get(i));
    }

    private SourceDefinition source(String file, int i) {
        return SourceDefinition.forFile("classpath:scale/package" + (i % PACKAGES) + "/" + file + ".xml", 10 + (i / PACKAGES) * 7);
    }

    private String name(String suffix, int i) {
        return pick(WORDS) + pick(WORDS) + suffix + i;
    }

    private <T> T pick(T[] list) {
        return list[random.nextInt(list.length)];
    }

    private Condition script(String text) {
        return Condition.builder().build(Script.builder().build("el", text));
    }

    private Action scriptAction(String text) {
        return Action.builder().build(Script.builder().build("el", text));
    }

    private Rule rule(int i) {
        String noun = pick(NOUNS), property = pick(PROPERTIES);
        var builder = Rule.builder().name(name("Rule", i), "Checks the " + noun + " " + property + " (generated rule " + i + ").")
                .source(source("rules", i));
        int kind = random.nextInt(10);
        if (kind == 0) {
            // A compiled rule: lambdas only, nothing to translate
            return builder.given(condition((Object order, Object customer) -> order != null && customer != null))
                    .then(action((Object order) -> {}))
                    .build();
        }
        String given = switch (random.nextInt(5)) {
            case 0 -> "#ctx." + noun + "." + property + " >= " + (10 + random.nextInt(900));
            case 1 -> "#ctx." + noun + "." + property + " == '" + pick(WORDS).toUpperCase() + "'";
            case 2 -> "#ctx." + noun + "." + property + " < ${scale." + noun + "." + property + ":" + (100 * (1 + random.nextInt(9))) + "}";
            case 3 -> "#ctx." + noun + " != null && #ctx." + noun + "." + property + " > 0";
            default -> "#ctx." + noun + "." + property + " >= 1 && #ctx." + pick(NOUNS) + "." + pick(PROPERTIES) + " <= " + (10 + random.nextInt(90));
        };
        builder.given(script(given));
        if (random.nextInt(3) == 0) builder.then(scriptAction("#ctx." + noun + ".flag" + (i % 7) + " = true"));
        if (random.nextInt(8) == 0) builder.otherwise(scriptAction("#ctx." + noun + ".flag" + (i % 7) + " = false"));
        return builder.build();
    }

    private RuleSet<?> ruleSet(int i, int ruleCount) {
        var builder = RuleSet.builder().with(name("Rules", i), "Generated rule set " + i + ".")
                .source(source("rulesets", i))
                .param(pick(NOUNS), Object.class, true, "The " + i + "th input");
        // A contiguous slice, so every rule is in at least one set, plus a few shared rules
        int chunk = Math.max(3, (int) Math.ceil(ruleCount / (double) Math.max(1, setsExpected())));
        int start = (i * chunk) % Math.max(1, ruleCount);
        int members = Math.min(ruleCount, 3 + random.nextInt(10));
        for (int k = 0; k < members; k++) builder.rule(rules.get((start + k) % ruleCount));
        for (int k = 0; k < 2; k++) if (random.nextBoolean()) builder.rule(rules.get(random.nextInt(ruleCount)));
        if (i % 3 == 0) {
            builder.validating().preCondition(script("#ctx." + pick(NOUNS) + " != null"))
                    .stopCondition(script("#ctx.ruleViolations.size() >= " + (1 + random.nextInt(4))));
        }
        return builder.build();
    }

    private int setsExpected() {
        return Math.max(1, Math.round(size * 0.15f));
    }

    private RuleFlow<?> flow(int i, int setCount, int flowCount) {
        var builder = RuleFlow.builder().name(name("Flow", i)).description("Generated flow " + i + ".")
                .source(source("flows", i))
                .param("order", Object.class, true, "The order")
                .param("customer", Object.class, false, "The customer")
                .bind("flowIndex", i);
        int runs = 2 + random.nextInt(4);
        for (int k = 0; k < runs; k++) {
            RuleSet<?> set = ruleSets.get((i * 3 + k) % setCount);
            if (k == 0) builder.run(set, spec -> spec.as("validation"));
            else builder.run(set);
        }
        builder.when(script("#ctx.validation != null && !#ctx.validation.isAllPass()"), b -> b.exit());
        Rule asyncRule = rules.get(random.nextInt(rules.size()));
        builder.asyncRun(asyncRule, spec -> spec.as("background").withImmutableBindings()
                        .onException(IllegalStateException.class, h -> h.bind("backgroundFailed", true)))
                .forEach(Function.builder().build(Script.builder().build("el", "#ctx.order.items")), "item",
                        b -> b.run(rules.get(random.nextInt(rules.size()))))
                .await("background", 5, TimeUnit.SECONDS)
                .when(script("#ctx.order.total >= " + (100 * (1 + random.nextInt(9)))),
                        b -> b.run(ruleSets.get(random.nextInt(setCount))),
                        b -> b.execute(scriptAction("#ctx.approved = true")));
        if (i % 5 == 0 && i > 0) builder.run(flows.get(random.nextInt(flows.size())));           // flows running flows
        if (i % 7 == 0) builder.run("scaleRules" + random.nextInt(setCount));                      // lookup by registry name
        if (i % 11 == 0) builder.run("missingRule" + i);                                            // unresolved on purpose
        if (i % 13 == 0) builder.run(name("Rule", random.nextInt(rules.size())));                   // name mismatch on purpose
        builder.onException(Exception.class, b -> b.execute(scriptAction("#ctx.approved = false")));
        builder.returning(Function.builder().build(Script.builder().build("el", "#ctx.approved")));
        return builder.build();
    }
}
