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
package com.acme.order.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import org.rulii.validation.beans.Expr;
import org.rulii.validation.beans.Validate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * An incoming order. Its constraints are validated with rulii-bean-validation: the Jakarta
 * annotations say what a well-formed order looks like, {@code @Validate} runs the registered
 * {@code ConsistentDatesRule} on the whole order, and the items cascade.
 */
@Validate("ConsistentDatesRule")
public class Order {

    @NotBlank
    private String id;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal total = BigDecimal.ZERO;

    @NotBlank
    private String shippingAddress;

    @DecimalMin("0")
    @DecimalMax("0.5")
    private BigDecimal discount = BigDecimal.ZERO;

    @PositiveOrZero
    private BigDecimal shippingCost = BigDecimal.ZERO;

    @NotNull
    @PastOrPresent
    private LocalDate orderDate;

    @Future
    @Expr(value = "ctx.orderDate == null || !ctx.requestedDeliveryDate.isBefore(ctx.orderDate)", lang = "js",
            errorCode = "order.delivery.before.order", message = "The requested delivery date is before the order date.")
    private LocalDate requestedDeliveryDate;

    @NotEmpty
    @Valid
    private List<@NotNull Item> items = new ArrayList<>();

    public Order() {
        super();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }

    public BigDecimal getShippingCost() {
        return shippingCost;
    }

    public void setShippingCost(BigDecimal shippingCost) {
        this.shippingCost = shippingCost;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
    }

    public LocalDate getRequestedDeliveryDate() {
        return requestedDeliveryDate;
    }

    public void setRequestedDeliveryDate(LocalDate requestedDeliveryDate) {
        this.requestedDeliveryDate = requestedDeliveryDate;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }
}
