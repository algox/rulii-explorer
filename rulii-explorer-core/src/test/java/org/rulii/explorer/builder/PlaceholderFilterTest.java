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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The key globs of {@link PlaceholderFilter#excluding(java.util.Collection)}.
 *
 */
class PlaceholderFilterTest {

    @Test
    void defaultExcludesHideTheUsualSecretNames() {
        PlaceholderFilter filter = PlaceholderFilter.excluding(PlaceholderFilter.DEFAULT_EXCLUDES);
        assertFalse(filter.show("db.password", "x"));
        assertFalse(filter.show("vendor.SecretKey", "x"), "case does not matter");
        assertFalse(filter.show("api.token", "x"));
        assertFalse(filter.show("aws.credentials.id", "x"));
        assertFalse(filter.show("signing.privateKey", "x"));
        assertTrue(filter.show("order.minTotal", "100"));
        assertTrue(filter.show("pricing.keyAccounts", "3"), "'key' alone is not a secret word");
    }

    @Test
    void globsMatchTheWholeKey() {
        PlaceholderFilter filter = PlaceholderFilter.excluding(List.of("pricing.vipDiscount", "loyalty.*", "shipping.?Over"));
        assertFalse(filter.show("pricing.vipDiscount", "0.1"));
        assertTrue(filter.show("pricing.vipDiscountLegacy", "0.1"), "no wildcard, no partial match");
        assertFalse(filter.show("loyalty.minTotal", "25"));
        assertTrue(filter.show("shipping.freeOver", "75"), "? matches one character, not four");
        assertFalse(filter.show("shipping.xOver", "75"));
    }

    @Test
    void regexCharactersInKeysAreLiteral() {
        PlaceholderFilter filter = PlaceholderFilter.excluding(List.of("a.b"));
        assertFalse(filter.show("a.b", "1"));
        assertTrue(filter.show("aXb", "1"));
    }

    @Test
    void emptyPatternsHideNothing() {
        assertSame(PlaceholderFilter.SHOW_ALL, PlaceholderFilter.excluding(null));
        assertSame(PlaceholderFilter.SHOW_ALL, PlaceholderFilter.excluding(List.of()));
        assertTrue(PlaceholderFilter.excluding(List.of(" ", "")).show("anything", "1"));
    }

    @Test
    void andCombines() {
        PlaceholderFilter filter = PlaceholderFilter.excluding(List.of("*secret*")).and((key, value) -> !"42".equals(value));
        assertFalse(filter.show("vendor.secret", "1"));
        assertFalse(filter.show("answer", "42"));
        assertTrue(filter.show("answer", "41"));
    }
}
