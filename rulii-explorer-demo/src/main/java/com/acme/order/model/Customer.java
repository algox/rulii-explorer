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

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import org.rulii.validation.beans.Expr;
import org.rulii.validation.beans.Validate;
import org.rulii.validation.beans.constraints.In;

import java.math.BigDecimal;

/**
 * The customer placing an order. Its {@code tier} runs a {@code @Validate} naming a rule nobody
 * registered, on purpose: the explorer reports it before a customer is ever validated.
 */
@Expr(value = "ctx.openBalance.compareTo(ctx.creditLimit) <= 0", lang = "js", errorCode = "customer.credit.exceeded",
        message = "The open balance exceeds the credit limit.")
public class Customer {

    @NotBlank
    private String id;

    @Min(18)
    private int age;

    @NotBlank
    @Email
    private String email;

    @Pattern(regexp = "\\+?[0-9 ]{7,20}")
    private String phone;

    @In({"STANDARD", "SILVER", "GOLD", "VIP"})
    @Validate("tierPolicyRule")
    private String tier = "STANDARD";

    @PositiveOrZero
    private BigDecimal openBalance = BigDecimal.ZERO;

    @PositiveOrZero
    private BigDecimal creditLimit = BigDecimal.ZERO;

    public Customer() {
        super();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public BigDecimal getOpenBalance() {
        return openBalance;
    }

    public void setOpenBalance(BigDecimal openBalance) {
        this.openBalance = openBalance;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit;
    }
}
