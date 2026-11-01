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
 * A constrained method, constructor or getter of a bean (FR-67).
 *
 * @param name            the method name, or the class's simple name for a constructor.
 * @param key             the library's flow-name stem ({@code order_placeOrder}, with type
 *                        suffixes for overloads): unique within the bean, used in rule ids and
 *                        deep links.
 * @param kind            method, constructor or getter.
 * @param signature       {@code name(param types)} with class names.
 * @param source          where it is declared; omitted with {@code include-sources=false}.
 * @param parameters      the parameters, in order, constrained or not.
 * @param crossParameter  what applies across the parameters; null when nothing does.
 * @param returnValue     the return value; null when it is neither constrained nor cascaded
 *                        (and for void).
 * @param binds           the {@code @Bind} declarations applying to the parameters.
 * @param parametersFlow  the parameters flow; null when no parameter is constrained.
 * @param returnValueFlow the return-value flow; null when the return value is not.
 * @param rules           the rules the library materialized for it.
 *
 * @author Algorithmx Development Team
 * @since 1.1
 */
public record ExecutableInfo(String name, String key, ExecutableKind kind, String signature, Source source,
                             List<ParameterInfo> parameters, CrossParameterInfo crossParameter, ReturnValueInfo returnValue,
                             List<ContextBindInfo> binds, FlowRef parametersFlow, FlowRef returnValueFlow,
                             List<BeanRule> rules) {

    public ExecutableInfo {
        parameters = Lists.copy(parameters);
        binds = Lists.copy(binds);
        rules = Lists.copy(rules);
    }
}
