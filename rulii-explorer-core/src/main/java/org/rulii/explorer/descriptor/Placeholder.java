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
package org.rulii.explorer.descriptor;

/**
 * A {@code ${key:default}} configuration placeholder of a script, in source order. The key and
 * the default are always as written (FR-14). The value is the one the script compiled with, and
 * is present only when the application opted in ({@code placeholderValues} on the application)
 * and the key passed its filter; {@code hidden} says the filter kept it back.
 *
 * @param key          the property key.
 * @param defaultValue the default as written; null when none.
 * @param value        the resolved value the compiler saw; null when not shown.
 * @param hidden       true when a value exists but the application's filter hides it; null otherwise.
 *
 * @author Max Arulananthan
 * @since 1.0
 *
 */
public record Placeholder(String key, String defaultValue, String value, Boolean hidden) {

    /** As written only: no value, not hidden. */
    public static Placeholder of(String key, String defaultValue) {
        return new Placeholder(key, defaultValue, null, null);
    }

    /** With the value the script compiled with. */
    public Placeholder withValue(String value) {
        return new Placeholder(key, defaultValue, value, null);
    }

    /** With the value kept back by the application's filter. */
    public Placeholder asHidden() {
        return new Placeholder(key, defaultValue, null, Boolean.TRUE);
    }
}
