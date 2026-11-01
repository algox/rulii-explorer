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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The plain-English phrase of a constraint (FR-69): {@code @DecimalMin("0.0")} reads
 * {@code is at least 0.0}, {@code @Size(min = 2, max = 5)} reads {@code size is between 2 and 5}.
 * Covers the Jakarta built-ins and rulii's own constraints; any other constraint reads as its
 * annotation, {@code @Name(attr=value, ..)}, which says what the reader needs without pretending
 * to understand it.
 *
 * <p>The phrase completes the member it is on ("total is at least 0.0"), so it starts with a
 * verb and never names the member.
 *
 * @author Algorithmx Development Team
 * @since 1.1
 */
public final class ConstraintPhrases {

    private static final String JAKARTA = "jakarta.validation.constraints.";
    private static final String RULII = "org.rulii.validation.beans.constraints.";

    private static final Map<String, Function<Map<String, Object>, String>> PHRASES = new HashMap<>();

    static {
        // Jakarta Bean Validation built-ins
        jakarta("AssertFalse", a -> "is false");
        jakarta("AssertTrue", a -> "is true");
        jakarta("DecimalMax", a -> (bool(a, "inclusive", true) ? "is at most " : "is less than ") + text(a, "value"));
        jakarta("DecimalMin", a -> (bool(a, "inclusive", true) ? "is at least " : "is more than ") + text(a, "value"));
        jakarta("Digits", a -> "has at most " + text(a, "integer") + " integer and " + text(a, "fraction") + " fraction digits");
        jakarta("Email", a -> "is an email address" + matching(a));
        jakarta("Future", a -> "is in the future");
        jakarta("FutureOrPresent", a -> "is in the present or the future");
        jakarta("Max", a -> "is at most " + text(a, "value"));
        jakarta("Min", a -> "is at least " + text(a, "value"));
        jakarta("Negative", a -> "is negative");
        jakarta("NegativeOrZero", a -> "is zero or negative");
        jakarta("NotBlank", a -> "is not blank");
        jakarta("NotEmpty", a -> "is not empty");
        jakarta("NotNull", a -> "is not null");
        jakarta("Null", a -> "is null");
        jakarta("Past", a -> "is in the past");
        jakarta("PastOrPresent", a -> "is in the past or the present");
        jakarta("Pattern", a -> "matches " + text(a, "regexp") + flags(a));
        jakarta("Positive", a -> "is positive");
        jakarta("PositiveOrZero", a -> "is zero or positive");
        jakarta("Size", a -> range("size", a, "min", "max", Integer.MAX_VALUE));

        // rulii's own
        rulii("Alpha", a -> "is letters" + (bool(a, "allowSpace", false) ? " and spaces" : "") + " only");
        rulii("AlphaNumeric", a -> "is letters and digits" + (bool(a, "allowSpace", false) ? " and spaces" : "") + " only");
        rulii("Ascii", a -> "is ASCII only");
        rulii("CodePointLength", a -> range("length in code points", a, "min", "max", Integer.MAX_VALUE));
        rulii("CreditCardNumber", a -> "is a credit card number");
        rulii("Decimal", a -> "is a decimal number");
        rulii("EAN", a -> "is an " + text(a, "type").replace("EAN", "EAN-") + " code");
        rulii("EndsWith", a -> "ends with " + oneOf(a, "value"));
        rulii("ISBN", a -> "is an " + text(a, "type").replace("ISBN_", "ISBN-"));
        rulii("In", a -> "is one of " + list(a, "value"));
        rulii("Length", a -> range("length", a, "min", "max", Integer.MAX_VALUE));
        rulii("LowerCase", a -> "is lower case");
        rulii("LuhnCheck", a -> "passes the Luhn check");
        rulii("Mod10Check", a -> "passes the Mod10 check");
        rulii("Mod11Check", a -> "passes the Mod11 check");
        rulii("Normalized", a -> "is Unicode normalized (" + text(a, "form") + ")");
        rulii("Numeric", a -> "is digits" + (bool(a, "allowSpace", false) ? " and spaces" : "") + " only");
        rulii("Range", a -> range("", a, "min", "max", Long.MAX_VALUE));
        rulii("StartsWith", a -> "starts with " + oneOf(a, "value"));
        rulii("URL", a -> "is a URL" + url(a));
        rulii("UUID", a -> "is a UUID");
        rulii("UniqueElements", a -> "has unique elements");
        rulii("UpperCase", a -> "is upper case");
        PHRASES.put(RULII + "time.DurationMax", a -> (bool(a, "inclusive", true) ? "is at most " : "is less than ") + duration(a));
        PHRASES.put(RULII + "time.DurationMin", a -> (bool(a, "inclusive", true) ? "is at least " : "is more than ") + duration(a));
    }

    private ConstraintPhrases() {
        super();
    }

    /**
     * @param annotationClassName the constraint annotation's class name.
     * @param attributes          its attributes, less message, groups and payload, with descriptor
     *                            values: numbers, strings, booleans, enum names, lists.
     * @return the phrase; never null.
     */
    public static String phrase(String annotationClassName, Map<String, Object> attributes) {
        Map<String, Object> safe = attributes == null ? Map.of() : attributes;
        Function<Map<String, Object>, String> phrase = PHRASES.get(annotationClassName);
        if (phrase != null) return phrase.apply(safe);

        String name = annotationClassName.substring(annotationClassName.lastIndexOf('.') + 1).replace('$', '.');
        if (safe.isEmpty()) return "@" + name;
        return "@" + name + "(" + safe.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", ")) + ")";
    }

    /** Whether the phrase book knows the constraint. */
    public static boolean knows(String annotationClassName) {
        return PHRASES.containsKey(annotationClassName);
    }

    private static void jakarta(String name, Function<Map<String, Object>, String> phrase) {
        PHRASES.put(JAKARTA + name, phrase);
    }

    private static void rulii(String name, Function<Map<String, Object>, String> phrase) {
        PHRASES.put(RULII + name, phrase);
    }

    // -------------------------------------------------------------------------------------------
    // Attribute readers
    // -------------------------------------------------------------------------------------------

    private static String text(Map<String, Object> attributes, String name) {
        Object value = attributes.get(name);
        return value == null ? "?" : String.valueOf(value);
    }

    private static boolean bool(Map<String, Object> attributes, String name, boolean fallback) {
        Object value = attributes.get(name);
        return value instanceof Boolean b ? b : fallback;
    }

    private static long number(Map<String, Object> attributes, String name, long fallback) {
        Object value = attributes.get(name);
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    private static List<String> values(Map<String, Object> attributes, String name) {
        Object value = attributes.get(name);
        List<String> result = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                result.add(String.valueOf(item));
            }
        } else if (value != null) {
            result.add(String.valueOf(value));
        }
        return result;
    }

    private static String list(Map<String, Object> attributes, String name) {
        return String.join(", ", values(attributes, name));
    }

    private static String oneOf(Map<String, Object> attributes, String name) {
        return String.join(" or ", values(attributes, name));
    }

    /** {@code size is between 2 and 5}, {@code size is at least 2}, {@code size is at most 5}. */
    private static String range(String subject, Map<String, Object> attributes, String minName, String maxName, long unbounded) {
        long min = number(attributes, minName, 0);
        long max = number(attributes, maxName, unbounded);
        String prefix = subject.isEmpty() ? "is " : subject + " is ";
        if (max >= unbounded) return prefix + "at least " + min;
        if (min <= 0) return prefix + "at most " + max;
        return prefix + "between " + min + " and " + max;
    }

    private static String matching(Map<String, Object> attributes) {
        String regexp = text(attributes, "regexp");
        return ".*".equals(regexp) || "?".equals(regexp) ? "" : " matching " + regexp + flags(attributes);
    }

    private static String flags(Map<String, Object> attributes) {
        List<String> flags = values(attributes, "flags");
        return flags.isEmpty() ? "" : " (flags " + String.join(", ", flags) + ")";
    }

    private static String url(Map<String, Object> attributes) {
        List<String> parts = new ArrayList<>();
        String protocol = text(attributes, "protocol");
        String host = text(attributes, "host");
        long port = number(attributes, "port", -1);
        String regexp = text(attributes, "regexp");
        if (!protocol.isEmpty() && !"?".equals(protocol)) parts.add("protocol " + protocol);
        if (!host.isEmpty() && !"?".equals(host)) parts.add("host " + host);
        if (port >= 0) parts.add("port " + port);
        if (!".*".equals(regexp) && !"?".equals(regexp)) parts.add("matching " + regexp);
        return parts.isEmpty() ? "" : " with " + String.join(", ", parts);
    }

    /** {@code 2 days 3 hours}; {@code 0 seconds} when every part is zero. */
    private static String duration(Map<String, Object> attributes) {
        List<String> parts = new ArrayList<>();
        for (String unit : List.of("days", "hours", "minutes", "seconds", "millis", "nanos")) {
            long value = number(attributes, unit, 0);
            if (value != 0) parts.add(value + " " + unit);
        }
        return parts.isEmpty() ? "0 seconds" : String.join(" ", parts);
    }
}
