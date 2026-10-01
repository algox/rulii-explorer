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
 * Which artifacts read and write a binding (FR-22).
 *
 * @param name           the binding name (the root of the paths read or written).
 * @param readBy         artifact ids that read it: declared parameters and script reads.
 * @param writtenBy      artifact ids that write it: flow binds and results, and script writes.
 * @param unknownWriters artifact ids that read it and run compiled code, which may write it.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public record BindingUsage(String name, List<String> readBy, List<String> writtenBy, List<String> unknownWriters) {

    public BindingUsage {
        readBy = Lists.copy(readBy);
        writtenBy = Lists.copy(writtenBy);
        unknownWriters = Lists.copy(unknownWriters);
    }
}
