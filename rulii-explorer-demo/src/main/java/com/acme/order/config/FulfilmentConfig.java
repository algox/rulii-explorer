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
 * A rule built in Java whose condition and action are Java source compiled by Janino at run
 * time. The script sees the bindings as {@code ctx} with their real types; the nightly flow runs
 * it for every catalog item.
 */
@Configuration
public class FulfilmentConfig {

    public FulfilmentConfig() {
        super();
    }

    @Bean
    public Rule backorderRule() {
        return Rule.builder()
                .name("BackorderRule", "Items with nothing in stock are marked as back-ordered.")
                .given(Condition.builder().build(Script.builder().build("java",
                        "ctx.item.getQuantity() <= 0")))
                .then(Action.builder().build(Script.builder().build("java",
                        "ctx.backordered = true;")))
                .build();
    }
}
