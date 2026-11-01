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
 * A {@code @Valid} on a property, container element, parameter or return value (FR-64).
 * Present only when cascaded.
 *
 * @param beanId           the bean the cascade reaches, when it is a described bean; null when the
 *                         target type is not (FR-61 adds every constrained target, so null means an
 *                         unconstrained type or one the explorer could not describe).
 * @param targetType       the declared type cascaded into.
 * @param groupConversions the group conversions, in declaration order.
 *
 * @author Algorithmx Development Team
 * @since 1.1
 */
public record CascadeInfo(String beanId, String targetType, List<GroupConversionInfo> groupConversions) {

    public CascadeInfo {
        groupConversions = Lists.copy(groupConversions);
    }
}
