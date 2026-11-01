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
package org.rulii.explorer.fixture.beans;

import jakarta.validation.ConstraintTarget;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.ConvertGroup;
import jakarta.validation.groups.Default;
import org.rulii.validation.Severity;
import org.rulii.validation.beans.Bind;
import org.rulii.validation.beans.Expr;
import org.rulii.validation.beans.Validate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * The root of the bean fixture: class, field and getter constraints, {@code @Validate} naming a
 * registered rule and a missing one, {@code @Expr} and {@code @Bind} at class and executable
 * level, cascades (plain, converted, in a list), container elements of a map, a constructor and
 * methods with parameter, cross-parameter and return-value declarations, and a property
 * inherited from an interface.
 *
 * <p>Scripts are rulii {@code java} scripts (Janino, on the test class path), which the explorer
 * analyses for bindings.
 */
@Validate("minTotalRule")
@Bind(name = "limit", value = "500", type = Integer.class)
@Bind(name = "twice", expr = "ctx.limit * 2", lang = "java", type = Integer.class)
@Expr(value = "ctx.total != null", lang = "java", errorCode = "order.total.present")
public class Order implements Priced {

    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal total;

    @Size(min = 2, max = 5)
    @Validate({"emailRequired", "prefixRule"})
    private String code;

    @Valid
    @NotNull
    @ConvertGroup(from = Default.class, to = Groups.Basic.class)
    private Customer customer;

    @Valid
    private List<@NotNull Line> lines;

    private Map<@NotBlank String, @Positive Integer> quantities;

    @Expr(value = "ctx.priority < 100", lang = "java", errorCode = "order.priority.range", message = "priority is out of range",
            severity = Severity.WARNING, groups = Groups.Advanced.class)
    private Integer priority;

    private BigDecimal price;

    public Order(@NotBlank String code) {
        super();
        this.code = code;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getCode() {
        return code;
    }

    public Customer getCustomer() {
        return customer;
    }

    public List<Line> getLines() {
        return lines;
    }

    public Map<String, Integer> getQuantities() {
        return quantities;
    }

    public Integer getPriority() {
        return priority;
    }

    @Override
    public BigDecimal getPrice() {
        return price;
    }

    @Positive
    public Integer getItemCount() {
        return lines == null ? 0 : lines.size();
    }

    @NotNull
    public Order merge(@Valid @NotNull Order other, @Min(1) int priority) {
        return this;
    }

    @Expr(value = "ctx.quantity > 0", lang = "java", errorCode = "order.add.quantity", applyTo = ConstraintTarget.PARAMETERS)
    @Bind(name = "maxQuantity", value = "10", type = Integer.class)
    public void add(@NotNull Line line, int quantity) {
    }
}
