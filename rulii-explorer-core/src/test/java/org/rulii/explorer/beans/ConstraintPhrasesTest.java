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
package org.rulii.explorer.beans;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The phrase book (FR-69): one phrase per built-in, attribute-aware, and a readable fallback.
 */
class ConstraintPhrasesTest {

    private static final String J = "jakarta.validation.constraints.";
    private static final String R = "org.rulii.validation.beans.constraints.";

    @Test
    void jakartaBuiltIns() {
        assertEquals("is not null", ConstraintPhrases.phrase(J + "NotNull", Map.of()));
        assertEquals("is at least 0.0", ConstraintPhrases.phrase(J + "DecimalMin", Map.of("value", "0.0", "inclusive", true)));
        assertEquals("is more than 0.0", ConstraintPhrases.phrase(J + "DecimalMin", Map.of("value", "0.0", "inclusive", false)));
        assertEquals("is less than 10", ConstraintPhrases.phrase(J + "DecimalMax", Map.of("value", "10", "inclusive", false)));
        assertEquals("is at least 18", ConstraintPhrases.phrase(J + "Min", Map.of("value", 18L)));
        assertEquals("is at most 99", ConstraintPhrases.phrase(J + "Max", Map.of("value", 99L)));
        assertEquals("size is between 2 and 5", ConstraintPhrases.phrase(J + "Size", Map.of("min", 2, "max", 5)));
        assertEquals("size is at least 2", ConstraintPhrases.phrase(J + "Size", Map.of("min", 2, "max", Integer.MAX_VALUE)));
        assertEquals("size is at most 5", ConstraintPhrases.phrase(J + "Size", Map.of("min", 0, "max", 5)));
        assertEquals("has at most 5 integer and 2 fraction digits", ConstraintPhrases.phrase(J + "Digits", Map.of("integer", 5, "fraction", 2)));
        assertEquals("matches \\d{5}", ConstraintPhrases.phrase(J + "Pattern", Map.of("regexp", "\\d{5}", "flags", List.of())));
        assertEquals("matches [a-z]+ (flags CASE_INSENSITIVE)", ConstraintPhrases.phrase(J + "Pattern", Map.of("regexp", "[a-z]+", "flags", List.of("CASE_INSENSITIVE"))));
        assertEquals("is an email address", ConstraintPhrases.phrase(J + "Email", Map.of("regexp", ".*", "flags", List.of())));
        assertEquals("is an email address matching .+@acme\\.com", ConstraintPhrases.phrase(J + "Email", Map.of("regexp", ".+@acme\\.com", "flags", List.of())));
        assertEquals("is in the past", ConstraintPhrases.phrase(J + "Past", Map.of()));
        assertEquals("is zero or positive", ConstraintPhrases.phrase(J + "PositiveOrZero", Map.of()));
    }

    @Test
    void ruliiConstraints() {
        assertEquals("is letters only", ConstraintPhrases.phrase(R + "Alpha", Map.of("allowSpace", false)));
        assertEquals("is letters and spaces only", ConstraintPhrases.phrase(R + "Alpha", Map.of("allowSpace", true)));
        assertEquals("length is between 1 and 10", ConstraintPhrases.phrase(R + "Length", Map.of("min", 1, "max", 10)));
        assertEquals("is between 1 and 10", ConstraintPhrases.phrase(R + "Range", Map.of("min", 1L, "max", 10L)));
        assertEquals("is at least 1", ConstraintPhrases.phrase(R + "Range", Map.of("min", 1L, "max", Long.MAX_VALUE)));
        assertEquals("is one of A, B", ConstraintPhrases.phrase(R + "In", Map.of("value", List.of("A", "B"))));
        assertEquals("starts with http or https", ConstraintPhrases.phrase(R + "StartsWith", Map.of("value", List.of("http", "https"))));
        assertEquals("is an EAN-13 code", ConstraintPhrases.phrase(R + "EAN", Map.of("type", "EAN13")));
        assertEquals("is an ISBN-13", ConstraintPhrases.phrase(R + "ISBN", Map.of("type", "ISBN_13")));
        assertEquals("is a URL", ConstraintPhrases.phrase(R + "URL", Map.of("protocol", "", "host", "", "port", -1, "regexp", ".*")));
        assertEquals("is a URL with protocol https, port 443", ConstraintPhrases.phrase(R + "URL", Map.of("protocol", "https", "host", "", "port", 443, "regexp", ".*")));
        assertEquals("is at most 2 days 12 hours", ConstraintPhrases.phrase(R + "time.DurationMax", Map.of("days", 2L, "hours", 12L, "inclusive", true)));
        assertEquals("is more than 30 seconds", ConstraintPhrases.phrase(R + "time.DurationMin", Map.of("seconds", 30L, "inclusive", false)));
        assertEquals("is at least 0 seconds", ConstraintPhrases.phrase(R + "time.DurationMin", Map.of()));
        assertEquals("passes the Luhn check", ConstraintPhrases.phrase(R + "LuhnCheck", Map.of()));
    }

    @Test
    void unknownConstraintsReadAsTheirAnnotation() {
        assertFalse(ConstraintPhrases.knows("com.acme.ValidCode"));
        assertEquals("@ValidCode", ConstraintPhrases.phrase("com.acme.ValidCode", Map.of()));
        assertEquals("@Codes.StrictCode", ConstraintPhrases.phrase("com.acme.Codes$StrictCode", null));
        assertEquals("@Range(max=10, min=1)", ConstraintPhrases.phrase("com.acme.Range", new java.util.TreeMap<>(Map.of("min", 1, "max", 10))));
    }
}
