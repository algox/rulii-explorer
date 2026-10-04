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
package org.rulii.explorer.fixture;

import org.rulii.explorer.descriptor.Token;
import org.rulii.explorer.expression.ExpressionAnalysis;
import org.rulii.explorer.expression.ExpressionAnalyzer;
import org.rulii.model.condition.Condition;
import org.rulii.rule.Rule;
import org.rulii.ruleflow.RuleFlow;
import org.rulii.ruleflow.RuleFlowExecutionContext;
import org.rulii.ruleflow.command.RuleFlowCommand;
import org.rulii.ruleset.RuleSet;
import org.rulii.script.Script;
import org.rulii.validation.Severity;
import org.rulii.validation.rules.Validators;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.rulii.model.action.Actions.action;
import static org.rulii.model.condition.Conditions.condition;
import static org.rulii.model.function.Functions.function;

/**
 * A Java-built order-processing application that exercises every artifact kind, every
 * command type and every problem code the descriptor can carry.
 *
 * <p>Registry names deliberately differ from own names where that matters: the rule named
 * {@code MinTotalRule} is registered as {@code minTotalRule}, so a by-name lookup of
 * {@code MinTotalRule} is a name mismatch.
 */
public final class OrderFixture {

    /** The rulii script language of the fixture's script-backed rule (Janino). */
    public static final String SCRIPT_LANGUAGE = "java";

    public final Rule stockAvailableRule = Rule.builder().build(StockAvailableRule.class);

    public final Rule minTotalRule = Rule.builder()
            .name("MinTotalRule", "Order total must meet the minimum.")
            .category(" Orders / Validation ").tags("total", "vip", "total")
            .given(condition((Integer total) -> total >= 100))
            .then(action((Integer total) -> {}))
            .build();

    public final Rule anotherMinTotalRule = Rule.builder()
            .name("MinTotalRule", "A second rule with the same name.")
            .given(condition((Integer total) -> total >= 200))
            .build();

    public final Rule scriptRule = Rule.builder()
            .name("ScriptRule", "Order total must meet the scripted minimum.")
            .given(Condition.builder().build(Script.builder().build(SCRIPT_LANGUAGE, "ctx.total >= 100")))
            .build();

    public final Rule emailRequired = Validators.notNull(Validators.binding("email"))
            .name("emailRequired").description("Customer email is required.")
            .errorCode("customer.email.missing").build();

    public final Rule minAge = Validators.min(Validators.binding("age"), 18)
            .name("minAge").description("Customer must be an adult.")
            .errorCode("customer.age.min").message("{0} must be at least 18").build();

    public final Rule customValidation = Rule.builder()
            .validationRule("CustomValidation", condition((Integer total) -> total > 0))
            .errorCode("order.total.invalid").severity(Severity.WARNING)
            .description("Order total must be positive.").build();

    public final Rule inlineRule = Rule.builder()
            .name("InlineRule", "Declared inside the rule set only.")
            .given(condition((Integer total) -> total != 0))
            .build();

    public final Rule inlineTarget = Rule.builder()
            .name("InlineTarget", "Run by the flow but never registered.")
            .given(condition(() -> true))
            .build();

    public final RuleSet<?> orderValidationRules = RuleSet.builder()
            .with("orderValidationRules", "Checks that an order is valid.")
            .category("Orders/Validation").tags("validation")
            .param("order", Object.class, true, "The order")
            .param("customer", Object.class, false)
            .validating()
            .preCondition(condition((Object order) -> order != null))
            .stopCondition(condition((Integer violationCount) -> violationCount >= 3))
            .rule(minTotalRule)
            .rule(emailRequired)
            .rule(inlineRule)
            .build();

    public final RuleFlow<?> orderProcessingFlow = RuleFlow.builder()
            .name("orderProcessingFlow")
            .description("Validates, scores, prices and approves an order.")
            .category("Orders")
            .param("order", Object.class, true, "The order")
            .bind("reviewQueue", "queue-a")                                                        // commands[0]
            .run(orderValidationRules, spec -> spec.as("validation"))                             // commands[1]
            .when(condition((Boolean approved) -> !approved), b -> b.exit())                     // commands[2]
            .asyncRun(scriptRule, spec -> spec.as("fraudCheck").withImmutableBindings()
                    .onException(IllegalStateException.class, h -> h.bind("fraudScore", 0.5)))    // commands[3]
            .await("fraudCheck", 5, TimeUnit.SECONDS)                                              // commands[4]
            .run("MinTotalRule")                                                                   // commands[5]  name mismatch
            .run("prefixRule")                                                                     // commands[6]  unresolved
            .run(StockAvailableRule.class)                                                         // commands[7]  by class
            .forEach(function(() -> List.of(1, 2)), "item", b -> b.execute(action((Integer item) -> {}))) // commands[8]
            .scope("tmp", b -> b.bind("scratch", 1))                                               // commands[9]
            .apply(function((Integer total) -> total * 2), spec -> spec.as("doubled"))             // commands[10]
            .run(inlineTarget)                                                                     // commands[11] inline target
            .command(new AuditCommand())                                                           // commands[12]
            .onException(Exception.class, b -> b.execute(action(() -> {})))
            .finalizer(action(() -> {}))
            .returning(Boolean.class, function((Boolean approved) -> approved))
            .build();

    public final RuleFlow<?> contextFlow = RuleFlow.builder()
            .name("contextFlow")
            .description("Runs in its own context.")
            .context(builder -> {}, "flowContext")
            .bind("a", 1)
            .build();

    /** A custom leaf command: opaque to the explorer. */
    public static final class AuditCommand implements RuleFlowCommand {
        public AuditCommand() {
            super();
        }

        @Override
        public void execute(RuleFlowExecutionContext ctx) {
            // writes an audit record
        }
    }

    /** A registry with everything registered under Spring-style bean names. */
    public MapRegistry registry() {
        return new MapRegistry()
                .register("stockAvailableRule", stockAvailableRule)
                .register("minTotalRule", minTotalRule)
                .register("anotherMinTotalRule", anotherMinTotalRule)
                .register("scriptRule", scriptRule)
                .register("emailRequired", emailRequired)
                .register("minAge", minAge)
                .register("customValidation", customValidation)
                .register("orderValidationRules", orderValidationRules)
                .register("orderProcessingFlow", orderProcessingFlow)
                .register("contextFlow", contextFlow);
    }

    /**
     * A stand-in analyzer for the fixture's script language: every script becomes one raw
     * token, and {@code ctx.<name>} references count as reads.
     */
    public static ExpressionAnalyzer stubAnalyzer() {
        return new ExpressionAnalyzer() {
            @Override
            public boolean supports(String language) {
                return SCRIPT_LANGUAGE.equals(language);
            }

            @Override
            public ExpressionAnalysis analyze(String sourceText) {
                List<String> reads = java.util.regex.Pattern.compile("ctx\\.([A-Za-z_][A-Za-z0-9_.]*)").matcher(sourceText)
                        .results().map(m -> m.group(1)).sorted().distinct().toList();
                return new ExpressionAnalysis(false, List.of(Token.raw(sourceText)), reads, List.of());
            }
        };
    }
}
