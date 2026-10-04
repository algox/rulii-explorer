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
package org.rulii.explorer.expression.plain;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The phrase book: how operators, well-known methods and identifiers read in English. Small on
 * purpose; anything not here is shown raw rather than guessed (FR-17).
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
public final class Phrases {

    private static final Map<String, String> OPERATORS = Map.ofEntries(
            Map.entry(">=", "is at least"),
            Map.entry(">", "is more than"),
            Map.entry("<=", "is at most"),
            Map.entry("<", "is less than"),
            Map.entry("==", "is"),
            Map.entry("!=", "is not"),
            Map.entry("and", "and"),
            Map.entry("&&", "and"),
            Map.entry("or", "or"),
            Map.entry("||", "or"),
            Map.entry("+", "plus"),
            Map.entry("-", "minus"),
            Map.entry("*", "times"),
            Map.entry("/", "divided by"),
            Map.entry("%", "modulo"),
            Map.entry("^", "to the power of"),
            Map.entry("matches", "matches the pattern"),
            Map.entry("instanceof", "is an instance of"),
            Map.entry("between", "is between"));

    /** Methods read as "<phrase> <receiver>": {@code violations.size()} is "number of violations". */
    private static final Map<String, String> PREFIX_METHODS = Map.of(
            "size", "number of",
            "length", "length of",
            "count", "number of",
            "get", "result of",
            "toUpperCase", "upper case of",
            "toLowerCase", "lower case of",
            "trim", "trimmed",
            "abs", "absolute value of");

    /** Methods read as "<receiver> <phrase> <arguments>". */
    private static final Map<String, String> INFIX_METHODS = Map.ofEntries(
            Map.entry("isEmpty", "is empty"),
            Map.entry("isBlank", "is blank"),
            Map.entry("isPresent", "is present"),
            Map.entry("contains", "contains"),
            Map.entry("containsKey", "has the key"),
            Map.entry("startsWith", "starts with"),
            Map.entry("endsWith", "ends with"),
            Map.entry("equals", "is"),
            Map.entry("equalsIgnoreCase", "is (ignoring case)"),
            Map.entry("add", "gets"),
            Map.entry("put", "gets"),
            Map.entry("remove", "loses"),
            Map.entry("isBefore", "is before"),
            Map.entry("isAfter", "is after"),
            Map.entry("compareTo", "compared to"));

    private static final Pattern CAMEL = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|_+");

    private Phrases() {
        super();
    }

    /** The English for a binary operator, or null when the phrase book has none. */
    public static String operator(String symbol) {
        return OPERATORS.get(symbol);
    }

    /** The prefix phrase of a method ("number of"), or null. */
    public static String prefixMethod(String name) {
        return PREFIX_METHODS.get(name);
    }

    /** The infix phrase of a method ("contains"), or null. */
    public static String infixMethod(String name) {
        return INFIX_METHODS.get(name);
    }

    /**
     * A method with no phrase of its own: {@code hasErrors} reads "has errors", {@code getTotal}
     * reads "total of" (prefix) and {@code canShip} reads "can ship".
     *
     * @return the phrase and whether it is a prefix ("x of receiver") or infix ("receiver x").
     */
    public static GenericMethod genericMethod(String name) {
        if (name.startsWith("get") && name.length() > 3) return new GenericMethod(humanize(name.substring(3)) + " of", true);
        return new GenericMethod(humanize(name), false);
    }

    public record GenericMethod(String phrase, boolean prefix) {
    }

    /** {@code minTotal} reads "min total"; {@code customerID} reads "customer id". */
    public static String humanize(String identifier) {
        if (identifier == null || identifier.isEmpty()) return identifier;
        return CAMEL.splitAsStream(identifier)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }

    /** {@code ["order", "shippingAddress"]} reads "order shipping address". */
    public static String humanize(List<String> path) {
        return path.stream().map(Phrases::humanize).collect(Collectors.joining(" "));
    }
}
