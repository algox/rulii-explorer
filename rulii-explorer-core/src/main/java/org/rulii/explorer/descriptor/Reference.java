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
 * A resolved reference from one artifact to another (FR-20). Unresolved targets are not
 * references; they appear on the command and in the problems.
 *
 * @param from       the referring artifact id.
 * @param to         the referenced artifact id.
 * @param type       contains or runs.
 * @param async      true for an async run.
 * @param path       where in the referring artifact: {@code members[2]} or {@code commands[1].then[0]}.
 * @param resolution direct, by-name or by-class.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public record Reference(String from, String to, ReferenceType type, boolean async, String path, Resolution resolution) {
}
