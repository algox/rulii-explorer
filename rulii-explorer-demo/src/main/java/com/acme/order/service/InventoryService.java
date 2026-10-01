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
package com.acme.order.service;

import com.acme.order.model.Item;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stock levels per SKU. In-memory for the showcase.
 */
public class InventoryService {

    private final Map<String, Integer> stock = new ConcurrentHashMap<>();

    public InventoryService() {
        super();
    }

    public boolean isInStock(Item item) {
        return stock.getOrDefault(item.getSku(), 0) >= item.getQuantity();
    }

    public void reserve(Item item) {
        stock.merge(item.getSku(), -item.getQuantity(), Integer::sum);
    }

    public void restock(String sku, int quantity) {
        stock.merge(sku, quantity, Integer::sum);
    }
}
