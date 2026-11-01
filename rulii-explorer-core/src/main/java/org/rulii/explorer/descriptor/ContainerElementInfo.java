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
 * A constrained or cascaded type argument of a container ({@code List<@NotBlank String>},
 * {@code Map<K, @Valid V>}), nested to any depth (FR-64).
 *
 * @param name              the element's name as the library reports it.
 * @param containerType     the container's class name.
 * @param typeArgumentIndex the type argument's index in the container type.
 * @param type              the element type's class name.
 * @param constraints       the constraints on the element, in declaration order.
 * @param cascade           the cascade, when the element is {@code @Valid}; null otherwise.
 * @param children          nested container elements.
 *
 * @author Algorithmx Development Team
 * @since 1.1
 */
public record ContainerElementInfo(String name, String containerType, int typeArgumentIndex, String type,
                                   List<ConstraintInfo> constraints, CascadeInfo cascade,
                                   List<ContainerElementInfo> children) {

    public ContainerElementInfo {
        constraints = Lists.copy(constraints);
        children = Lists.copy(children);
    }
}
