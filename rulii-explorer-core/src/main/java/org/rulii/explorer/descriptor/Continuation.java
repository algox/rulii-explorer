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
 * The continuation of an async run: the name the result is bound under and the commands run.
 *
 * @param as   binding name of the result.
 * @param body the commands run, in order.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
public record Continuation(String as, List<Command> body) {

    public Continuation {
        body = Lists.copy(body);
    }
}
