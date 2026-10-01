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
 * What a bind step, or the {@code with} parameters of a run step, binds.
 *
 * @param scope target scope; null for the current scope.
 * @param kind  how values are obtained: literal, declarations, bindings, map, bean, loader or expression.
 * @param names the names bound, where known.
 * @param label the bean name or class of the source, for bean and loader binds; null otherwise.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public record BindInfo(String scope, String kind, List<BoundName> names, String label) {

    public BindInfo {
        names = Lists.copy(names);
    }
}
