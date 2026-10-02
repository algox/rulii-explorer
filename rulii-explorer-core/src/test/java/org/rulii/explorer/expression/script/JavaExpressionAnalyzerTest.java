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
 * The Java (Janino) translations, pinned by a golden corpus ({@code golden/java-corpus.md});
 * regenerate with {@code -Dgolden.update=true} and review the diff.
 */
class JavaExpressionAnalyzerTest {

    private static final Path GOLDEN = Path.of("src/test/resources/golden/java-corpus.md");

    private static final List<String> CORPUS = List.of(
            // the demo's fulfilment rules
            "ctx.order.getTotal().doubleValue() >= ${shipping.expressOver:200}",
            "ctx.shippingMethod = \"EXPRESS\";",
            "ctx.shippingMethod = \"STANDARD\";",
            "ctx.order.getItems().size() > ${shipping.splitAbove:5}",
            "int parcels = (ctx.order.getItems().size() + 4) / 5;\nctx.parcels = parcels;",
            "ctx.order.getRequestedDeliveryDate() != null && ctx.order.getRequestedDeliveryDate().isAfter(ctx.order.getOrderDate())",
            "long days = java.time.temporal.ChronoUnit.DAYS.between(ctx.order.getOrderDate(), ctx.order.getRequestedDeliveryDate());\nctx.deliveryDays = (int) days;",
            "ctx.item.getQuantity() <= 0",
            "ctx.backordered = true;",
            // rulii's own Janino tests
            "true",
            "ctx.age >= 18",
            "ctx.age >= 18;",
            "int nextAge = ctx.age + 1;\nnextAge >= 18;",
            "int threshold = 80;\nreturn ctx.score >= threshold;",
            "ctx.role.equals(\"admin\")",
            "ctx.value > ctx.min && ctx.value < ctx.max",
            "ctx.balance >= 100.0",
            "ctx.active",
            "ctx.result = 42;",
            "ctx.msg = \"hello\";",
            "ctx.sum = ctx.a + ctx.b;",
            "ctx.fullName = ctx.firstName + \" \" + ctx.lastName;",
            "int temp = ctx.x * ctx.x;\nctx.out = temp - 1;",
            "ctx.category = ctx.age >= 18 ? \"adult\" : \"minor\";",
            "ctx.grade = ctx.score >= 90 ? \"A\" : ctx.score >= 80 ? \"B\" : \"C\";",
            "ctx.result = Math.abs(-99);",
            "ctx.result = Math.max(ctx.a, ctx.b);",
            "ctx.output = ctx.input.trim().toUpperCase();",
            "ctx.counter = ctx.counter + 1;",
            "ctx.setValueOrBind(\"finalized\", true);",
            // Java of its own
            "final double rate = ${tax.rate:0.2};\nctx.tax = ctx.order.getTotal().doubleValue() * rate;",
            "java.util.List<String> names = ctx.customer.getNames();\nnames.size() > 1",
            "String tier = (String) ctx.customer.getTier();\ntier.equals(\"VIP\")",
            "ctx.limit = 500L;",
            "ctx.ratio = 0.5d * ctx.weight;",
            "ctx.initial = 'A';",
            "ctx.ok = ctx.order.isPaid() && !ctx.order.getItems().isEmpty();",
            "ctx.first = ctx.order.getItems().get(0).getSku();",
            "ctx.day = java.time.LocalDate.now().getDayOfWeek();",
            "ctx.total = java.math.BigDecimal.valueOf(ctx.amount).add(ctx.order.getTotal());",
            "ctx.names = ctx.order.getItems().stream().map(i -> i.getSku()).toList();",
            "ctx.order.getItems().forEach(Item::reserve);",
            "ctx.copy = new java.util.ArrayList<String>(ctx.names);",
            "ctx.order instanceof com.acme.order.model.Order",
            "ctx.flags & 4",
            // does not parse
            "for (Item item : ctx.order.getItems()) ctx.n++;",
            "ctx.age >= 18 &&",
            "");

    private final JavaExpressionAnalyzer analyzer = new JavaExpressionAnalyzer();

    private static String plain(ExpressionAnalysis analysis) {
        return analysis.tokens().stream().map(Token::text).collect(Collectors.joining(" "));
    }

    @Test
    void supportsJavaOnly() {
        assertTrue(analyzer.supports("java"));
        assertTrue(analyzer.supports("Java"));
        assertTrue(analyzer.supports("janino"));
        assertFalse(analyzer.supports("js"));
        assertFalse(analyzer.supports("el"));
        assertFalse(analyzer.supports(null));
    }

    @Test
    void gettersAreTheProperty() {
        ExpressionAnalysis analysis = analyzer.analyze("ctx.order.getTotal().doubleValue() >= ${shipping.expressOver:200}");
        assertTrue(analysis.complete());
        assertEquals("order total is at least shipping.expressOver (default 200)", plain(analysis));
        assertEquals(List.of("order.total"), analysis.reads());
        assertEquals(List.of("order", "total"), analysis.tokens().get(0).path());

        ExpressionAnalysis spel = new SpelExpressionAnalyzer().analyze("#ctx.order.total >= ${shipping.expressOver:200}");
        assertEquals(plain(spel), plain(analysis), "the same sentence as the SpEL property access");
        assertEquals(spel.reads(), analysis.reads());

        ExpressionAnalysis chained = analyzer.analyze("ctx.order.getItems().size() > ${shipping.splitAbove:5}");
        assertEquals("number of order items is more than shipping.splitAbove (default 5)", plain(chained));
        assertEquals(List.of("order.items"), chained.reads());
    }

    @Test
    void typedLocalsCastsAndStaticCalls() {
        ExpressionAnalysis analysis = analyzer.analyze("long days = java.time.temporal.ChronoUnit.DAYS.between(ctx.order.getOrderDate(), ctx.order.getRequestedDeliveryDate());\nctx.deliveryDays = (int) days;");
        assertFalse(analysis.complete(), "the static call is not phrased");
        assertEquals("let days be java.time.temporal.ChronoUnit.DAYS.between ( order order date , order requested delivery date ) then set delivery days to days", plain(analysis));
        assertEquals(List.of("order.orderDate", "order.requestedDeliveryDate"), analysis.reads(), "the arguments of a raw call still count");
        assertEquals(List.of("deliveryDays"), analysis.writes());
        assertEquals(Token.RAW, analysis.tokens().get(3).t());

        ExpressionAnalysis parcels = analyzer.analyze("int parcels = (ctx.order.getItems().size() + 4) / 5;\nctx.parcels = parcels;");
        assertTrue(parcels.complete());
        assertEquals("let parcels be number of order items plus 4 divided by 5 then set parcels to parcels", plain(parcels));
    }

    @Test
    void conditionsAutoReturnTheLastExpression() {
        ExpressionAnalysis analysis = analyzer.analyze("int threshold = 80;\nreturn ctx.score >= threshold;");
        assertTrue(analysis.complete());
        assertEquals("let threshold be 80 then score is at least threshold", plain(analysis));
        assertEquals(List.of("score"), analysis.reads());
    }

    @Test
    void unparsableScriptHasNoTokens() {
        for (String script : List.of("for (Item item : ctx.order.getItems()) ctx.n++;", "ctx.age >= 18 &&", "  ")) {
            ExpressionAnalysis analysis = analyzer.analyze(script);
            assertFalse(analysis.complete(), script);
            assertTrue(analysis.tokens().isEmpty(), script);
        }
    }

    @Test
    void corpusMatchesTheGoldenTable() throws IOException {
        StringBuilder table = new StringBuilder();
        table.append("# Java corpus\n\n");
        table.append("Generated by `JavaExpressionAnalyzerTest`; rerun with `-Dgolden.update=true` to regenerate.\n\n");
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
