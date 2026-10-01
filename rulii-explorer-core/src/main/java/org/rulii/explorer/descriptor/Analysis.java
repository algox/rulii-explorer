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

import java.util.List;

/**
 * The plain-English rendering of a script expression.
 *
 * @param complete false when any token is {@code raw}: part of the expression could not be translated.
 * @param tokens   the rendering, in order; empty when the expression could not be parsed at all.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public record Analysis(boolean complete, List<Token> tokens) {

    public Analysis {
        tokens = Lists.copy(tokens);
    }
}
