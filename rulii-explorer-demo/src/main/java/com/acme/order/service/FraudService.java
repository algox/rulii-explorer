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

import com.acme.order.model.Customer;
import com.acme.order.model.Order;

import java.math.BigDecimal;

/**
 * Scores the fraud risk of an order from 0 (safe) to 1. A stand-in for a remote service.
 */
public class FraudService {

    public FraudService() {
        super();
    }

    public double score(Order order, Customer customer) {
        if (order.getTotal() == null) throw new FraudServiceException("order has no total");
        double amount = order.getTotal().min(BigDecimal.valueOf(10_000)).doubleValue() / 10_000;
        double newCustomer = customer.getOpenBalance().signum() == 0 ? 0.2 : 0;
        return Math.min(1.0, amount * 0.7 + newCustomer);
    }
}
