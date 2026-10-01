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
 * Something worth fixing (FR-23).
 *
 * @param severity error, warning or info.
 * @param code     a stable code such as {@code UNRESOLVED_TARGET}.
 * @param artifact the artifact id concerned; null for application-wide problems.
 * @param path     where in the artifact ({@code commands[2]}); null when not applicable.
 * @param message  what is wrong, in plain words.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public record Problem(ProblemSeverity severity, String code, String artifact, String path, String message) {
}
