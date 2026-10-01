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

import com.acme.order.model.Customer;
import com.acme.order.model.Order;
import com.acme.order.service.FraudService;
import org.rulii.bind.Bindings;
import org.rulii.rule.Rule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.rulii.model.action.Actions.action;
import static org.rulii.model.condition.Conditions.condition;

/**
 * Fraud scoring: a rule built in Java with lambdas. Its logic is compiled code, which the
 * explorer shows as such (honest opacity) with the declared signatures.
 */
@Configuration
public class RiskConfig {

    public RiskConfig() {
        super();
    }

    @Bean
    public FraudService fraudService() {
        return new FraudService();
    }

    @Bean
    public Rule fraudScoreRule(FraudService fraudService) {
        return Rule.builder()
                .name("FraudScoreRule", "Scores the fraud risk of an order from 0 (safe) to 1.")
                .given(condition((Order order, Customer customer) -> order != null && customer != null))
                .then(action((Order order, Customer customer, Bindings bindings) ->
                        bindings.setValueOrBind("fraudScore", fraudService.score(order, customer))))
                .build();
    }
}
