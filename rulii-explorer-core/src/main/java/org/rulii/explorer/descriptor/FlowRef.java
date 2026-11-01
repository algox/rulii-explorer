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
 * The flow the library builds for a bean or an executable (FR-63, FR-67). Bean flows are not
 * artifacts: the name is what an application that embeds validation in a flow of its own runs,
 * and the bean page is where the flow's content is shown.
 *
 * @param name the flow's name, as {@code RuleFlowAssembler} names it.
 *
 * @author Algorithmx Development Team
 * @since 1.1
 */
public record FlowRef(String name) {
}
