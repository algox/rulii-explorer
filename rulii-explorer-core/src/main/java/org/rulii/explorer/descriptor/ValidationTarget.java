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
 * One name a {@code @Validate} runs, resolved against the registry at describe time (FR-64,
 * FR-77). The library itself resolves the name only when the bean is validated.
 *
 * @param name       the name as declared.
 * @param artifactId the registry artifact it resolves to; null when nothing is registered under
 *                   that name, which is also reported as an {@code UNRESOLVED_TARGET} problem.
 *
 * @author Algorithmx Development Team
 * @since 1.1
 */
public record ValidationTarget(String name, String artifactId) {
}
