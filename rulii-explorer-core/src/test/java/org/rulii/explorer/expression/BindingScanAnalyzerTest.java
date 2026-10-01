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
package org.rulii.explorer.expression;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reads and writes recovered from JavaScript and Java scripts through the {@code ctx} convention.
 */
class BindingScanAnalyzerTest {

    private final BindingScanAnalyzer analyzer = new BindingScanAnalyzer();

    @Test
    void supportsTheShippedScriptLanguagesButNotSpel() {
        assertTrue(analyzer.supports("js"));
        assertTrue(analyzer.supports("JavaScript"));
        assertTrue(analyzer.supports("java"));
        assertFalse(analyzer.supports("el"));
        assertFalse(analyzer.supports(null));
        assertTrue(new BindingScanAnalyzer(Set.of("groovy")).supports("groovy"));
    }

    @Test
    void readsAndWritesFromJavaStatements() {
        ExpressionAnalysis analysis = analyzer.analyze("int temp = ctx.x * ctx.x;\nctx.out = temp - 1;");
        assertEquals(List.of("x"), analysis.reads());
        assertEquals(List.of("out"), analysis.writes());
        assertFalse(analysis.complete());
        assertTrue(analysis.tokens().isEmpty(), "no translation, the UI shows the script as written");
    }

    @Test
    void readsAndWritesFromJavaScript() {
        ExpressionAnalysis analysis = analyzer.analyze("var temp = ctx.order.total * 2; ctx.order.discount = temp + 5; ctx.counter++;");
        assertEquals(List.of("counter", "order.total"), analysis.reads());
        assertEquals(List.of("counter", "order.discount"), analysis.writes());
    }

    @Test
    void comparisonsAreReadsAndCompoundAssignmentsAreBoth() {
        assertEquals(List.of("age"), analyzer.analyze("ctx.age >= 18").reads());
        assertTrue(analyzer.analyze("ctx.age == 18").writes().isEmpty());
        assertTrue(analyzer.analyze("ctx.a != ctx.b").writes().isEmpty());

        ExpressionAnalysis plusEquals = analyzer.analyze("ctx.sum += ctx.n;");
        assertEquals(List.of("n", "sum"), plusEquals.reads());
        assertEquals(List.of("sum"), plusEquals.writes());
    }

    @Test
    void stringsCommentsAndOtherObjectsAreIgnored() {
        ExpressionAnalysis analysis = analyzer.analyze("""
                // ctx.ignored = 1
                ctx.msg = "ctx.notABinding"; /* ctx.alsoIgnored */
                myctx.other = 2; foo.ctx.bar = 3;
                """);
        assertEquals(List.of("msg"), analysis.writes());
        assertTrue(analysis.reads().isEmpty());
    }

    @Test
    void blankScriptIsUnparsed() {
        assertEquals(ExpressionAnalysis.unparsed(), analyzer.analyze("  "));
    }
}
