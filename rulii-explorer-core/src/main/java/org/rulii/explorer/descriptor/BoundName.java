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
 * A name a bind step binds. Values are never recorded (NFR-3).
 *
 * @param name       binding name.
 * @param type       value type name when known; null otherwise.
 * @param expression the expression that produces the value, when there is one; null otherwise.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public record BoundName(String name, String type, Expression expression) {
}
