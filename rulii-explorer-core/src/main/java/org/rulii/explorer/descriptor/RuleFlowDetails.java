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
 * What a rule flow does (FR-13): the complete command tree.
 *
 * @param context       label of the context configurator (an XML bean name, or a class name); null when the flow runs in the caller context.
 * @param commands      the top-level commands, in order.
 * @param globalHandler the flow-level exception handler; null when none.
 * @param finalizer     action run last; null when none.
 * @param returning     the result extractor; null when the flow returns its rule context.
 * @param resultType    the result type name; null when the flow returns its rule context.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
public record RuleFlowDetails(String context, List<Command> commands, Handler globalHandler, Expression finalizer,
                              Expression returning, String resultType) {

    public RuleFlowDetails {
        commands = Lists.copy(commands);
    }
}
