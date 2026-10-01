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
package com.acme.order.rules;

import com.acme.order.model.Item;
import com.acme.order.model.Order;
import com.acme.order.service.InventoryService;
import org.rulii.annotation.Given;
import org.rulii.annotation.Rule;
import org.rulii.annotation.Then;

/**
 * Every line must be in stock; when it is, the stock is reserved. The explorer cannot see
 * what the compiled action writes, so the inventory is reported as a possible unknown write.
 * Deliberately has no {@code @Description}.
 */
@Rule("StockAvailableRule")
public class StockAvailableRule {

    public StockAvailableRule() {
        super();
    }

    @Given
    public boolean when(Order order, InventoryService inventory) {
        return order.getItems().stream().allMatch(inventory::isInStock);
    }

    @Then
    public void then(Order order, InventoryService inventory) {
        for (Item item : order.getItems()) inventory.reserve(item);
    }
}
