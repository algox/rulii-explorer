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

import org.rulii.rule.Rule;
import org.rulii.ruleflow.RuleFlow;
import org.rulii.ruleset.RuleSet;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

import static org.rulii.model.action.Actions.action;
import static org.rulii.model.condition.Conditions.condition;

/**
 * A small application with one rule, one rule set and one flow, auto-configured by rulii-spring
 * and the explorer starter.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class ExplorerTestApplication {

    @Bean
    public Rule minTotalRule() {
        return Rule.builder()
                .name("MinTotalRule", "Order total must meet the minimum.")
                .given(condition((Integer total) -> total >= 100))
                .then(action((Integer total) -> {}))
                .build();
    }

    @Bean
    public RuleSet<?> orderRules(Rule minTotalRule) {
        return RuleSet.builder().with("orderRules", "Checks an order.").rule(minTotalRule).build();
    }

    @Bean
    public RuleFlow<?> orderFlow(RuleSet<?> orderRules) {
        return RuleFlow.builder().name("orderFlow").description("Runs the order rules.")
                .run(orderRules, spec -> spec.as("result"))
                .run("missingRule")
                .build();
    }
}
