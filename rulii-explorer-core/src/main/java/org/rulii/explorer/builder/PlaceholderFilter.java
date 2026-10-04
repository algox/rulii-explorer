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

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Decides which {@code ${key:default}} placeholder values the descriptor may show. Passing a filter
 * to {@link DescriptorBuilder#placeholderValues(PlaceholderFilter)} turns values on; a key the
 * filter refuses is reported as hidden, with its key and default still visible.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 *
 */
@FunctionalInterface
public interface PlaceholderFilter {

    /** The key patterns hidden by default: the usual names of secrets. Case-insensitive globs. */
    List<String> DEFAULT_EXCLUDES = List.of("*password*", "*secret*", "*token*", "*credential*", "*private*");

    /** Shows every value. Combine with {@link #excluding(Collection)} to hide some. */
    PlaceholderFilter SHOW_ALL = (key, value) -> true;

    /**
     * @param key   the property key as written in the placeholder.
     * @param value the value the script compiled with.
     * @return true to show the value, false to report the placeholder as hidden.
     */
    boolean show(String key, String value);

    /** A filter that shows a value only when both this and {@code other} do. */
    default PlaceholderFilter and(PlaceholderFilter other) {
        Objects.requireNonNull(other, "other cannot be null.");
        return (key, value) -> show(key, value) && other.show(key, value);
    }

    /**
     * Hides the keys matching any of the patterns. A pattern is a case-insensitive glob over the
     * whole key: {@code *} matches any run of characters, {@code ?} one character, anything else
     * itself. {@code *secret*} hides {@code vendor.secretKey}; {@code pricing.vipDiscount} hides
     * that key only.
     *
     * @param patterns the key patterns; null or empty hides nothing.
     * @return the filter.
     */
    static PlaceholderFilter excluding(Collection<String> patterns) {
        if (patterns == null || patterns.isEmpty()) return SHOW_ALL;
        List<Pattern> compiled = patterns.stream().filter(Objects::nonNull).map(String::strip).filter(p -> !p.isEmpty())
                .map(PlaceholderFilter::glob).toList();
        return (key, value) -> key == null || compiled.stream().noneMatch(p -> p.matcher(key).matches());
    }

    private static Pattern glob(String pattern) {
        StringBuilder regex = new StringBuilder();
        for (String part : pattern.split("(?<=[*?])|(?=[*?])")) {
            switch (part) {
                case "*" -> regex.append(".*");
                case "?" -> regex.append('.');
                default -> regex.append(Pattern.quote(part));
            }
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
