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
package org.rulii.explorer.expression.script;

import org.junit.jupiter.api.Test;
import org.rulii.explorer.descriptor.Token;
import org.rulii.explorer.expression.ExpressionAnalysis;
import org.rulii.explorer.expression.spel.SpelExpressionAnalyzer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The JavaScript translations, pinned by a golden corpus ({@code golden/js-corpus.md}) the way
 * the SpEL ones are; regenerate with {@code -Dgolden.update=true} and review the diff.
 */
class JsExpressionAnalyzerTest {

    private static final Path GOLDEN = Path.of("src/test/resources/golden/js-corpus.md");

    private static final List<String> CORPUS = List.of(
            // the demo's loyalty rules
            "ctx.order.total.doubleValue() >= ${loyalty.minTotal:25}",
            "ctx.points = Math.floor(ctx.order.total.doubleValue()) * (ctx.customer.tier === 'VIP' ? 2 : 1);",
            "ctx.points = 0;",
            "ctx.order.items.size() >= ${loyalty.bulkItems:10}",
            "ctx.points += ${loyalty.bulkBonus:100};",
            "ctx.customer.age >= ${loyalty.seniorAge:65}",
            "ctx.seniorDiscount = ${loyalty.seniorDiscount:0.05};",
            "ctx.points >= ${loyalty.goldPoints:1000} && ctx.customer.tier !== 'GOLD'",
            "ctx.customer.setTier('GOLD');\nctx.upgraded = true;",
            "ctx.points > 0",
            "ctx.points",
            // the SpEL corpus, in JavaScript
            "ctx.age >= 18",
            "ctx.age != null",
            "ctx.age !== undefined",
            "ctx.age == null",
            "ctx.age <= 150",
            "ctx.approved = true",
            "ctx.count = ctx.count + 1",
            "ctx.count++",
            "ctx.name == '\\${literal}'",
            "ctx.order.length > 0",
            "ctx.person",
            "ctx.seed * 2",
            "ctx.setValue('finalized', true)",
            "ctx.getValue('total') > 10",
            "ctx.customer.openBalance + ctx.order.total <= ctx.customer.creditLimit",
            "ctx.customer.tier == 'VIP'",
            "ctx.order.discount = ${pricing.vipDiscount:0.10}",
            "ctx.violations.size() >= 3",
            "ctx.validation.hasErrors()",
            "ctx.reviewQueue.add(ctx.order)",
            "!ctx.items.isEmpty()",
            "ctx.items.contains('promo')",
            "ctx.name.startsWith('A')",
            "ctx.order.getTotal()",
            "ctx.order.canShip()",
            "(ctx.a || ctx.b) && ctx.c",
            "ctx.a || ctx.b && ctx.c",
            "ctx.a && ctx.b && ctx.c",
            "ctx.items[0].price",
            "ctx.map['key']",
            "-ctx.balance",
            "[1, 2, 3]",
            "true",
            // JavaScript of its own
            "ctx.total >= 500 ? 'review' : 'auto'",
            "ctx.name ?? 'anonymous'",
            "Math.max(ctx.a, ctx.b) > ${limit:5}",
            "Math.abs(ctx.delta) < 0.01",
            "Math.pow(ctx.base, 2)",
            "Number(ctx.quantity) * ctx.item.price",
            "ctx.order.id.toString()",
            "ctx.order.items.length === 0",
            "ctx.total -= ctx.discount; ctx.total *= 1.2; ctx.total /= 2",
            "let subtotal = ctx.order.total - ctx.order.discount; ctx.payable = subtotal + ctx.order.shippingCost;",
            "var score = 0; score = score + 1; ctx.score = score",
            "if (ctx.customer.tier === 'VIP') { ctx.discount = 0.1 } else { ctx.discount = 0 }",
            "if (ctx.order.total > 100) ctx.flag = true",
            "return ctx.age >= 18",
            "return ctx.a; ctx.b = 1",
            "ctx.age >= 18 // adults only",
            "ctx.greeting = `hello`",
            "ctx.a ** 2",
            // raw fallbacks (never guessed)
            "typeof ctx.value === 'number'",
            "ctx.items.map(i => i.price)",
            "ctx.result = new Date()",
            "ctx.flags & 4",
            "ctx.email.match(/^[^@]+@[^@]+$/) != null",
            "someGlobal > 5",
            "ctx.order instanceof Object",
            "ctx.greeting = `hello ${ctx.name}`",
            "ctx.result = {a: 1}",
            "this.x",
            // does not parse
            "ctx.age >= 18 &&",
            "for (const i of ctx.items) ctx.n++",
            "ctx.a = 1 ctx.b = 2",
            "");

    private final JsExpressionAnalyzer analyzer = new JsExpressionAnalyzer();

    private static String plain(ExpressionAnalysis analysis) {
        return analysis.tokens().stream().map(Token::text).collect(Collectors.joining(" "));
    }

    @Test
    void supportsJavaScriptOnly() {
        assertTrue(analyzer.supports("js"));
        assertTrue(analyzer.supports("JavaScript"));
        assertTrue(analyzer.supports("ecmascript"));
        assertFalse(analyzer.supports("el"));
        assertFalse(analyzer.supports("java"));
        assertFalse(analyzer.supports(null));
    }

    @Test
    void readsLikeItsSpelTwin() {
        SpelExpressionAnalyzer spel = new SpelExpressionAnalyzer();
        for (String[] pair : new String[][] {
                {"ctx.order.total >= ${order.minTotal:100}", "#ctx.order.total >= ${order.minTotal:100}"},
                {"ctx.customer.openBalance + ctx.order.total <= ctx.customer.creditLimit", "#ctx.customer.openBalance + #ctx.order.total <= #ctx.customer.creditLimit"},
                {"ctx.customer.tier === 'VIP'", "#ctx.customer.tier == 'VIP'"},
                {"ctx.order.discount = ${pricing.vipDiscount:0.10}", "#ctx.order.discount = ${pricing.vipDiscount:0.10}"},
                {"ctx.violations.size() >= 3", "#ctx.violations.size() >= 3"},
                {"!ctx.items.isEmpty()", "!#ctx.items.isEmpty()"},
                {"(ctx.a || ctx.b) && ctx.c", "(#ctx.a || #ctx.b) && #ctx.c"},
                {"ctx.total >= 500 ? 'review' : 'auto'", "#ctx.total >= 500 ? 'review' : 'auto'"},
                {"ctx.setValue('finalized', true)", "#ctx.setValue('finalized', true)"}}) {
            ExpressionAnalysis js = analyzer.analyze(pair[0]);
            ExpressionAnalysis el = spel.analyze(pair[1]);
            assertEquals(plain(el), plain(js), pair[0]);
            assertEquals(el.reads(), js.reads(), pair[0]);
            assertEquals(el.writes(), js.writes(), pair[0]);
            assertTrue(js.complete(), pair[0]);
        }
    }

    @Test
    void placeholdersBecomeTokensAndAreNeverResolved() {
        ExpressionAnalysis analysis = analyzer.analyze("ctx.order.total.doubleValue() >= ${loyalty.minTotal:25}");
        assertTrue(analysis.complete());
        assertEquals("order total is at least loyalty.minTotal (default 25)", plain(analysis));
        assertEquals(List.of("order.total"), analysis.reads(), "the unwrapping call is transparent");
        Token placeholder = analysis.tokens().get(2);
        assertEquals(Token.PLACEHOLDER, placeholder.t());
        assertEquals("loyalty.minTotal", placeholder.key());
        assertEquals("25", placeholder.defaultValue());
    }

    @Test
    void statementsAreActionsAndSettersAreWrites() {
        ExpressionAnalysis analysis = analyzer.analyze("ctx.customer.setTier('GOLD');\nctx.upgraded = true;");
        assertTrue(analysis.complete());
        assertEquals("set customer tier to \"GOLD\" then set upgraded to true", plain(analysis));
        assertEquals(List.of(), analysis.reads());
        assertEquals(List.of("customer.tier", "upgraded"), analysis.writes());

        ExpressionAnalysis compound = analyzer.analyze("ctx.points += ${loyalty.bulkBonus:100};");
        assertEquals("add loyalty.bulkBonus (default 100) to points", plain(compound));
        assertEquals(List.of("points"), compound.reads());
        assertEquals(List.of("points"), compound.writes());
    }

    @Test
    void bareNamesAreNotBindings() {
        ExpressionAnalysis global = analyzer.analyze("someGlobal > 5");
        assertFalse(global.complete());
        assertEquals(Token.RAW, global.tokens().get(0).t());
        assertTrue(global.reads().isEmpty());

        ExpressionAnalysis local = analyzer.analyze("let subtotal = ctx.order.total - ctx.order.discount; ctx.payable = subtotal + ctx.order.shippingCost;");
        assertTrue(local.complete());
        assertEquals("let subtotal be order total minus order discount then set payable to subtotal plus order shipping cost", plain(local));
        assertEquals(List.of("order.discount", "order.shippingCost", "order.total"), local.reads());
        assertEquals(List.of("payable"), local.writes());
    }

    @Test
    void untranslatablePartsAreRawAndMarkedIncomplete() {
        ExpressionAnalysis analysis = analyzer.analyze("ctx.items.map(i => i.price).length > ${limit:5}");
        assertFalse(analysis.complete());
        assertEquals("length of items map i => i.price is more than limit (default 5)", plain(analysis), "translated around the lambda");
        assertTrue(analysis.tokens().stream().anyMatch(t -> Token.RAW.equals(t.t()) && t.text().equals("i => i.price")), "the lambda is raw, as written");
        assertEquals(List.of("items"), analysis.reads(), "reads around a raw part survive");
        assertEquals(Token.PLACEHOLDER, analysis.tokens().get(analysis.tokens().size() - 1).t(), "the rest is still translated");
    }

    @Test
    void unparsableScriptHasNoTokens() {
        for (String script : List.of("ctx.age >= 18 &&", "for (const i of ctx.items) ctx.n++", "ctx.a = 1 ctx.b = 2", "  ")) {
            ExpressionAnalysis analysis = analyzer.analyze(script);
            assertFalse(analysis.complete(), script);
            assertTrue(analysis.tokens().isEmpty(), script);
            assertTrue(analysis.reads().isEmpty(), script);
        }
    }

    @Test
    void corpusMatchesTheGoldenTable() throws IOException {
        StringBuilder table = new StringBuilder();
        table.append("# JavaScript corpus\n\n");
        table.append("Generated by `JsExpressionAnalyzerTest`; rerun with `-Dgolden.update=true` to regenerate.\n\n");
        table.append("| Expression | Plain English | Complete | Reads | Writes |\n");
        table.append("|---|---|---|---|---|\n");

        for (String expression : CORPUS) {
            ExpressionAnalysis analysis = analyzer.analyze(expression);
            table.append("| `").append(expression.isEmpty() ? " " : expression.replace("|", "\\|").replace("\n", "⏎")).append("` | ")
                    .append(plain(analysis).replace("|", "\\|").replace("\n", "⏎")).append(" | ")
                    .append(analysis.complete() ? "yes" : "no").append(" | ")
                    .append(String.join(", ", analysis.reads())).append(" | ")
                    .append(String.join(", ", analysis.writes())).append(" |\n");
        }

        String actual = table.toString();

        if (Boolean.getBoolean("golden.update") || !Files.exists(GOLDEN)) {
            Files.createDirectories(GOLDEN.getParent());
            Files.writeString(GOLDEN, actual, StandardCharsets.UTF_8);
            fail("Golden corpus written to " + GOLDEN.toAbsolutePath() + "; review it and run again.");
        }

        String golden = Files.readString(GOLDEN, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertEquals(golden, actual, "corpus translations changed; rerun with -Dgolden.update=true if intended");
    }
}
