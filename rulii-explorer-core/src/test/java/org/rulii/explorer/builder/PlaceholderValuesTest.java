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
package org.rulii.explorer.builder;

import org.junit.jupiter.api.Test;
import org.rulii.explorer.descriptor.Placeholder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading placeholder values back from the source and the compiled text: certain cases give
 * values, doubtful ones give none. Never a guess.
 *
 */
class PlaceholderValuesTest {

    private static List<String> values(String source, String resolved) {
        return PlaceholderValues.resolve(source, resolved).stream().map(Placeholder::value).toList();
    }

    @Test
    void noPlaceholdersNoEntries() {
        assertTrue(PlaceholderValues.resolve("#ctx.total >= 100", "#ctx.total >= 100").isEmpty());
        assertTrue(PlaceholderValues.resolve(null, null).isEmpty());
        assertTrue(PlaceholderValues.asWritten("ctx.total > 1").isEmpty());
    }

    @Test
    void oneValue() {
        List<Placeholder> found = PlaceholderValues.resolve("#ctx.order.total >= ${order.minTotal:100}", "#ctx.order.total >= 150");
        assertEquals(1, found.size());
        assertEquals("order.minTotal", found.get(0).key());
        assertEquals("100", found.get(0).defaultValue());
        assertEquals("150", found.get(0).value());
        assertNull(found.get(0).hidden());
    }

    @Test
    void severalValuesInSourceOrder() {
        assertEquals(List.of("0.25", "10", "x"),
                values("a > ${p.one} && b < ${p.two:5} || c == '${p.three}'", "a > 0.25 && b < 10 || c == 'x'"));
    }

    @Test
    void valueThatRepeatsTheSurroundingTextIsAmbiguous() {
        // "a + b" and "2", or "a" and "b + 2": both fit, so neither is reported.
        assertEquals(nulls(2), values("${x} + ${y} + 1", "a + b + 2 + 1"));
        // With no repetition the two directions agree.
        assertEquals(List.of("a", "2"), values("${x} + ${y} + 1", "a + 2 + 1"));
    }

    @Test
    void ambiguousValuesGiveNone() {
        // Left-to-right reads "1" and "2 + 3"; right-to-left reads "1 + 2" and "3": no agreement, no values.
        assertEquals(nulls(2), values("${x} + ${y}", "1 + 2 + 3"));
        // Adjacent placeholders cannot be split at all.
        assertEquals(nulls(2), values("${a}${b}", "12"));
    }

    @Test
    void mismatchedTextsGiveNone() {
        assertEquals(nulls(1), values("x > ${a}", "y > 1"), "different prefix");
        assertEquals(nulls(1), values("x > ${a} && y", "x > 1"), "missing suffix");
        assertEquals(nulls(2), values("${a} and ${b}", "1 or 2"), "inner anchor missing");
    }

    @Test
    void unresolvedOrUnknownCompiledTextGivesNone() {
        assertEquals(nulls(1), values("x > ${a:1}", "x > ${a:1}"), "nothing was resolved");
        assertEquals(nulls(1), values("x > ${a:1}", null), "compiled text unknown");
    }

    @Test
    void escapedPlaceholdersAreNotPlaceholders() {
        assertTrue(PlaceholderValues.asWritten("'\\${literal}'").isEmpty());
    }

    @Test
    void asWrittenCarriesKeysAndDefaultsOnly() {
        List<Placeholder> found = PlaceholderValues.asWritten("${a:1} ${ b }");
        assertEquals(2, found.size());
        assertEquals(Placeholder.of("a", "1"), found.get(0));
        assertEquals(Placeholder.of("b", null), found.get(1));
    }

    @Test
    void recordHelpers() {
        Placeholder p = Placeholder.of("k", "d");
        assertEquals(new Placeholder("k", "d", "v", null), p.withValue("v"));
        assertEquals(new Placeholder("k", "d", null, Boolean.TRUE), p.asHidden());
        assertNull(p.asHidden().value(), "a hidden placeholder never carries its value");
    }

    private static List<String> nulls(int n) {
        return java.util.Collections.nCopies(n, null);
    }
}
