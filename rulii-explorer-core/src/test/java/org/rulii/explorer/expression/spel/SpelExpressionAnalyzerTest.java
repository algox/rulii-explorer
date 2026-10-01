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
package org.rulii.explorer.expression.spel;

import org.junit.jupiter.api.Test;
import org.rulii.explorer.descriptor.Token;
import org.rulii.explorer.expression.ExpressionAnalysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The SpEL analyzer against the expression corpus: every expression in rulii-spring's XML
 * fixtures and the design brief, plus the awkward cases. The golden corpus is a Markdown table
 * so a reviewer can read the translations; rerun with {@code -Dgolden.update=true} after an
 * intended change.
 */
class SpelExpressionAnalyzerTest {

    private static final Path GOLDEN = Path.of("src/test/resources/golden/spel-corpus.md");

    private static final List<String> CORPUS = List.of(
            // rulii-spring fixtures
            "#ctx.age >= 18",
            "age >= 18",
            "#ctx.age != null",
            "#ctx.age <= 150",
            "#ctx.age >= ${placeholder.absent:18}",
            "#ctx.approved = true",
            "#ctx.count = #ctx.count + 1",
            "#ctx.sum = #ctx.sum + #ctx.n",
            "#ctx.continued = #ctx.squared",
            "#ctx.count == 2",
            "#ctx.go == true",
            "#ctx.name == '\\${literal}'",
            "#ctx.order.length() > 0",
            "#ctx.person",
            "#ctx.seed * 2",
            "#ctx.setValue('finalized', true)",
            "#ctx.snapshot = #ctx.log + '|' + #ctx.doubled + '|' + #ctx.tally + '|' + #ctx.withCheck",
            "#ctx.f1.get() + #ctx.f2.get()",
            "{1, 2, 3}",
            "true",
            // design brief (order-service demo)
            "#ctx.customer.age >= 18",
            "#ctx.order.total >= ${order.minTotal:100}",
            "#ctx.order.shippingAddress != null",
            "#ctx.customer.openBalance + #ctx.order.total <= #ctx.customer.creditLimit",
            "#ctx.fraudScore < 0.8",
            "#ctx.fraudScore >= 0.5",
            "#ctx.customer.tier == 'VIP'",
            "#ctx.order.discount = ${pricing.vipDiscount:0.10}",
            "#ctx.order.total >= ${shipping.freeOver:75}",
            "#ctx.order.shippingCost = 0",
            "#ctx.item.price > 0 && #ctx.item.price < ${pricing.maxPrice:10000}",
            "#ctx.order != null",
            "#ctx.violations.size() >= 3",
            "#ctx.validation.hasErrors()",
            "#ctx.reviewQueue.add(#ctx.order)",
            "#ctx.order.total >= 500",
            "#ctx.approved",
            // more SpEL
            "#ctx.customer.email matches '^[^@]+@[^@]+$'",
            "#ctx.total >= 500 ? 'review' : 'auto'",
            "#ctx.name ?: 'anonymous'",
            "!#ctx.items.isEmpty()",
            "#ctx.items.contains('promo')",
            "#ctx.name.startsWith('A')",
            "#ctx.order.getTotal()",
            "#ctx.order.canShip()",
            "(#ctx.a || #ctx.b) && #ctx.c",
            "#ctx.a || #ctx.b && #ctx.c",
            "#ctx.a && #ctx.b && #ctx.c",
            "#ctx.items[0].price",
            "#ctx.map['key']",
            "#ctx.value between {1, 10}",
            "-#ctx.balance",
            "#ctx.getValue('total') > 10",
            // raw fallbacks (never guessed)
            "#ctx.values.?[#this > 10]",
            "T(java.lang.Math).max(#ctx.a, #ctx.b)",
            "@orderService.price(#ctx.order)",
            "#ex.message",
            "#ctx.status instanceof T(String)",
            "new java.util.ArrayList()",
            // does not parse
            "#ctx.age >= 18 &&",
            "");

    private final SpelExpressionAnalyzer analyzer = new SpelExpressionAnalyzer();

    private static String plain(ExpressionAnalysis analysis) {
        return analysis.tokens().stream().map(Token::text).collect(Collectors.joining(" "));
    }

    @Test
    void supportsSpelOnly() {
        assertTrue(analyzer.supports("el"));
        assertTrue(analyzer.supports("EL"));
        assertTrue(analyzer.supports("spel"));
        assertFalse(analyzer.supports("js"));
        assertFalse(analyzer.supports(null));
    }

    @Test
    void placeholdersBecomeTokensAndAreNeverResolved() {
        ExpressionAnalysis analysis = analyzer.analyze("#ctx.order.total >= ${order.minTotal:100}");

        assertTrue(analysis.complete());
        assertEquals("order total is at least order.minTotal (default 100)", plain(analysis));
        assertEquals(List.of("order.total"), analysis.reads());
        assertTrue(analysis.writes().isEmpty());

        Token binding = analysis.tokens().get(0);
        assertEquals(Token.BINDING, binding.t());
        assertEquals(List.of("order", "total"), binding.path());

        Token placeholder = analysis.tokens().get(2);
        assertEquals(Token.PLACEHOLDER, placeholder.t());
        assertEquals("order.minTotal", placeholder.key());
        assertEquals("100", placeholder.defaultValue());

        ExpressionAnalysis noDefault = analyzer.analyze("#ctx.age >= ${min.age}");
        assertEquals("age is at least min.age", plain(noDefault));
        assertNull(noDefault.tokens().get(2).defaultValue());
    }

    @Test
    void assignmentsAreWrites() {
        ExpressionAnalysis analysis = analyzer.analyze("#ctx.count = #ctx.count + 1");
        assertEquals("set count to count plus 1", plain(analysis));
        assertEquals(List.of("count"), analysis.reads());
        assertEquals(List.of("count"), analysis.writes());

        ExpressionAnalysis setValue = analyzer.analyze("#ctx.setValue('finalized', true)");
        assertEquals("set finalized to true", plain(setValue));
        assertEquals(List.of("finalized"), setValue.writes());
        assertTrue(setValue.complete());

        ExpressionAnalysis nested = analyzer.analyze("#ctx.order.discount = ${pricing.vipDiscount:0.10}");
        assertEquals(List.of("order.discount"), nested.writes());
        assertTrue(nested.reads().isEmpty());
    }

    @Test
    void bareNamesAndCtxPathsAreTheSameBinding() {
        assertEquals(plain(analyzer.analyze("age >= 18")), plain(analyzer.analyze("#ctx.age >= 18")));
        assertEquals(analyzer.analyze("age >= 18").reads(), analyzer.analyze("#ctx.age >= 18").reads());
    }

    @Test
    void untranslatablePartsAreRawAndMarkedIncomplete() {
        ExpressionAnalysis analysis = analyzer.analyze("T(java.lang.Math).max(#ctx.a, #ctx.b) > ${limit:5}");
        assertFalse(analysis.complete());
        assertEquals(Token.RAW, analysis.tokens().get(0).t());
        assertEquals("T(java.lang.Math).max(#ctx.a, #ctx.b)", analysis.tokens().get(0).text(), "the source slice, as written");
        assertEquals(Token.OP, analysis.tokens().get(1).t());
        assertEquals(Token.PLACEHOLDER, analysis.tokens().get(2).t(), "the rest is still translated");
    }

    @Test
    void unparsableScriptHasNoTokens() {
        ExpressionAnalysis analysis = analyzer.analyze("#ctx.age >= 18 &&");
        assertFalse(analysis.complete());
        assertTrue(analysis.tokens().isEmpty());
        assertTrue(analysis.reads().isEmpty());
        assertEquals(ExpressionAnalysis.unparsed(), analyzer.analyze("  "));
    }

    @Test
    void mixedAndOrKeepsItsGrouping() {
        assertEquals("( a or b ) and c", plain(analyzer.analyze("(#ctx.a || #ctx.b) && #ctx.c")));
        assertEquals("a or ( b and c )", plain(analyzer.analyze("#ctx.a || #ctx.b && #ctx.c")));
        assertEquals("a and b and c", plain(analyzer.analyze("#ctx.a && #ctx.b && #ctx.c")));
    }

    @Test
    void corpusMatchesTheGoldenTable() throws IOException {
        StringBuilder table = new StringBuilder();
        table.append("# SpEL corpus\n\n");
        table.append("Generated by `SpelExpressionAnalyzerTest`; rerun with `-Dgolden.update=true` to regenerate.\n\n");
        table.append("| Expression | Plain English | Complete | Reads | Writes |\n");
        table.append("|---|---|---|---|---|\n");

        for (String expression : CORPUS) {
            ExpressionAnalysis analysis = analyzer.analyze(expression);
            table.append("| `").append(expression.isEmpty() ? " " : expression.replace("|", "\\|")).append("` | ")
                    .append(plain(analysis).replace("|", "\\|")).append(" | ")
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
