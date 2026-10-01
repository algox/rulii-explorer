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

import com.acme.order.service.InventoryService;
import com.acme.order.service.ReviewQueue;
import org.rulii.bind.Bindings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The services the rules talk to, and the bindings the processing flow starts from.
 */
@Configuration
public class OrderConfig {

    public OrderConfig() {
        super();
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public InventoryService inventoryService() {
        InventoryService inventory = new InventoryService();
        inventory.restock("SKU-1", 100);
        inventory.restock("SKU-2", 25);
        return inventory;
    }

    @Bean
    public ReviewQueue reviewQueue() {
        return new ReviewQueue();
    }

    /**
     * The services the order flow binds before it runs anything, so rules can reach them as
     * {@code #ctx.reviewQueue}, {@code #ctx.inventory} and {@code #ctx.clock}.
     */
    @Bean
    public Bindings orderServices(ReviewQueue reviewQueue, InventoryService inventoryService, Clock clock) {
        Bindings bindings = Bindings.builder().standard();
        bindings.bind("reviewQueue", reviewQueue);
        bindings.bind("inventory", inventoryService);
        bindings.bind("clock", clock);
        return bindings;
    }
}
