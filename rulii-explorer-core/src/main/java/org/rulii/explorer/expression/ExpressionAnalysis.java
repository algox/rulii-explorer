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

import org.rulii.explorer.descriptor.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What an {@link ExpressionAnalyzer} found in a script: its plain-English tokens and the
 * binding paths it reads and writes.
 *
 * @param complete false when any part could not be translated (a {@code raw} token), or when
 *                 the script could not be parsed at all (no tokens).
 * @param tokens   the plain-English rendering, in order; empty when parsing failed.
 * @param reads    binding paths read, such as {@code order.total}; sorted, no duplicates.
 * @param writes   binding paths written; sorted, no duplicates.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
public record ExpressionAnalysis(boolean complete, List<Token> tokens, List<String> reads, List<String> writes) {

    public ExpressionAnalysis {
        tokens = copy(tokens);
        reads = copy(reads);
        writes = copy(writes);
    }

    /** The analysis of a script that could not be parsed: no tokens, nothing known. */
    public static ExpressionAnalysis unparsed() {
        return new ExpressionAnalysis(false, List.of(), List.of(), List.of());
    }

    private static <T> List<T> copy(List<T> list) {
        return list == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(list));
    }
}
