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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small facts about rulii itself that the builder needs: which classes are rulii's own, and
 * how to sort ids and paths that contain indexes.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
final class Rulii {

    /** Packages of rulii's own runtime classes (never an application's, unlike {@code org.rulii.explorer}). */
    private static final List<String> INTERNAL_PACKAGES = List.of("org.rulii.rule.", "org.rulii.ruleset.",
            "org.rulii.ruleflow.", "org.rulii.validation.", "org.rulii.model.", "org.rulii.script.", "org.rulii.bind.",
            "org.rulii.context.", "org.rulii.registry.", "org.rulii.lib.");

    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private Rulii() {
        super();
    }

    /** Whether a class is one of rulii's own runtime classes (including its lambdas). */
    static boolean isInternalClass(Class<?> type) {
        return type != null && isInternalClass(type.getName());
    }

    static boolean isInternalClass(String className) {
        return className != null && INTERNAL_PACKAGES.stream().anyMatch(className::startsWith);
    }

    /**
     * Natural order: digit runs compare as numbers, so {@code commands[2]} sorts before
     * {@code commands[11]}.
     */
    static int compareNatural(String a, String b) {
        if (a == null || b == null) return a == null ? (b == null ? 0 : -1) : 1;

        Matcher ma = NUMBER.matcher(a);
        Matcher mb = NUMBER.matcher(b);
        int ia = 0;
        int ib = 0;

        while (true) {
            boolean fa = ma.find(ia);
            boolean fb = mb.find(ib);
            String ta = fa ? a.substring(ia, ma.start()) : a.substring(ia);
            String tb = fb ? b.substring(ib, mb.start()) : b.substring(ib);
            int text = ta.compareTo(tb);
            if (text != 0) return text;
            if (!fa || !fb) return fa == fb ? 0 : (fa ? 1 : -1);

            int number = Long.compare(Long.parseLong(ma.group()), Long.parseLong(mb.group()));
            if (number != 0) return number;
            ia = ma.end();
            ib = mb.end();
        }
    }
}
