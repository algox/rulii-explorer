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
package com.acme.order.config;

import org.rulii.model.action.Action;
import org.rulii.model.condition.Condition;
import org.rulii.rule.Rule;
import org.rulii.script.Script;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A rule built in Java whose condition and action are JavaScript, run by GraalJS. The script sees
 * the bindings as {@code ctx}; the explorer keeps the text and scans it for what it reads and
 * writes, so {@code ctx.upgraded} shows up as a write while the call to {@code setTier} does not.
 *
 * <p>The flow in {@code rules/pricing/loyalty.xml} runs this rule by its bean name,
 * {@code tierUpgradeRule}, which is the right way round (compare {@link PricingConfig}).
 */
@Configuration
public class LoyaltyConfig {

    public LoyaltyConfig() {
        super();
    }

    @Bean
    public Rule tierUpgradeRule() {
        return Rule.builder()
                .name("TierUpgradeRule", "Customers who have earned enough points move up to the GOLD tier.")
                .category("Pricing/Loyalty").tags("loyalty", "vip")
                .given(Condition.builder().build(Script.builder().build("js",
                        "ctx.points >= ${loyalty.goldPoints:1000} && ctx.customer.tier !== 'GOLD'")))
                .then(Action.builder().build(Script.builder().build("js",
                        "ctx.customer.setTier('GOLD');\nctx.upgraded = true;")))
                .build();
    }
}
