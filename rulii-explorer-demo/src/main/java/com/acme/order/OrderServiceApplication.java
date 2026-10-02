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

import org.rulii.spring.annotation.RuleScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The rulii explorer showcase: an order-processing service whose rules, rule sets and flows
 * exercise every artifact kind the explorer can show. Rules come from XML files, from
 * {@code @Rule} classes and from Java builders; two deliberate defects make the problems list
 * interesting.
 *
 * <p>Run it and open {@code /actuator/rulii} (JSON) or {@code /rulii/} (UI).
 *
 * @author Max Arulananthan
 * @since 1.0
 */
@SpringBootApplication
@RuleScan(scanBasePackages = "com.acme.order.rules",
        xmlLocations = {"classpath:rules/order/", "classpath:rules/pricing/"})
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
