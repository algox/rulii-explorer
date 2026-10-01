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

/**
 * Translates the scripts of one language into plain English and finds the bindings they read
 * and write (FR-17, FR-22). Implementations must be deterministic and never guess: a part they
 * cannot translate becomes a {@code raw} token.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public interface ExpressionAnalyzer {

    /**
     * Whether this analyzer handles the language.
     *
     * @param language the script language name, such as {@code el}.
     * @return true when {@link #analyze(String)} can be called for scripts in this language.
     */
    boolean supports(String language);

    /**
     * Analyses a script as written, placeholders unresolved.
     *
     * @param sourceText the script text; never null.
     * @return the analysis; never null. {@link ExpressionAnalysis#unparsed()} when the text does not parse.
     */
    ExpressionAnalysis analyze(String sourceText);
}
