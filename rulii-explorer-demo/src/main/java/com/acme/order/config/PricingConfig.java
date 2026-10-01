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

import org.rulii.model.condition.Condition;
import org.rulii.rule.Rule;
import org.rulii.script.Script;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Pricing bits declared in Java: a rule built with a SpEL script, and the defaults the pricing
 * rules read.
 *
 * <p>The bean is {@code rangeCheckRule} while the rule calls itself {@code RangeCheckRule}. The
 * nightly flow looks it up by the rule name, which is the deliberate name-mismatch defect the
 * explorer warns about: registry lookups use bean names.
 */
@Configuration
public class PricingConfig {

    public PricingConfig() {
        super();
    }

    @Bean
    public Rule rangeCheckRule() {
        return Rule.builder()
                .name("RangeCheckRule", "Catalog item prices must be positive and below the configured maximum.")
                .given(Condition.builder().build(Script.builder().build("el",
                        "#ctx.item.price > 0 && #ctx.item.price < ${pricing.maxPrice:10000}")))
                .build();
    }

    @Bean
    public Map<String, Object> pricingDefaults() {
        return Map.of("vipTier", "VIP", "freeShippingOver", 75);
    }
}
