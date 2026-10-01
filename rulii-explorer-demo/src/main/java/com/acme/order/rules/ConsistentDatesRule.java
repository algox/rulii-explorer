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

import com.acme.order.model.Order;
import org.rulii.annotation.Given;
import org.rulii.annotation.Rule;

import java.time.Clock;
import java.time.LocalDate;

/**
 * The requested delivery date must not be before the order date, and the order date must not
 * be in the future. Deliberately has no {@code @Description}, so the explorer reports it.
 */
@Rule("ConsistentDatesRule")
public class ConsistentDatesRule {

    public ConsistentDatesRule() {
        super();
    }

    @Given
    public boolean when(Order order, Clock clock) {
        LocalDate today = LocalDate.now(clock);
        LocalDate ordered = order.getOrderDate() != null ? order.getOrderDate() : today;
        LocalDate delivery = order.getRequestedDeliveryDate() != null ? order.getRequestedDeliveryDate() : ordered;
        return !ordered.isAfter(today) && !delivery.isBefore(ordered);
    }
}
