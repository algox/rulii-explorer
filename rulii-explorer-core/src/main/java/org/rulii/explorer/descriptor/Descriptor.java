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
 * The whole application, described once: every artifact, the references between them, the
 * bindings they read and write, and the problems found. This is the public contract (FR-40):
 * the UI, CI and documentation tools read only this document.
 *
 * <p>Output is stable (FR-43): lists are sorted, keys are in declaration order, and nothing in
 * the body depends on when it was built.
 *
 * @param descriptorVersion the contract version, {@code "1.1"}; additive changes keep the major (1.1 added artifact category and tags).
 * @param application       the application and the library versions.
 * @param packages          every package an artifact belongs to, sorted by id.
 * @param artifacts         every artifact, sorted by type then id.
 * @param references        every resolved reference, sorted by from, path, to.
 * @param bindings          binding usage, sorted by name.
 * @param problems          problems found, most severe first.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
public record Descriptor(String descriptorVersion, ApplicationInfo application, List<PackageInfo> packages,
                         List<Artifact> artifacts, List<Reference> references, List<BindingUsage> bindings,
                         List<Problem> problems) {

    public Descriptor {
        packages = Lists.copy(packages);
        artifacts = Lists.copy(artifacts);
        references = Lists.copy(references);
        bindings = Lists.copy(bindings);
        problems = Lists.copy(problems);
    }
}
