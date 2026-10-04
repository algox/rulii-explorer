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
 * A declared parameter: a rule method parameter, or a rule set or rule flow input parameter.
 *
 * @param name          binding name.
 * @param type          the type name.
 * @param required      whether the binding must exist.
 * @param defaultValue  the default, as text; null when there is none.
 * @param matchStrategy the binding matching strategy class simple name when one is declared; null otherwise.
 * @param description   what the parameter is for; null when not given.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
public record Parameter(String name, String type, boolean required, String defaultValue, String matchStrategy,
                        String description) {
}
