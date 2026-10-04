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
package org.rulii.explorer.problem;

/**
 * The problem codes the explorer reports. Stable: tools may key on them.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class ProblemCodes {

    /** error: a by-name or by-class run target that resolves to nothing now. */
    public static final String UNRESOLVED_TARGET = "UNRESOLVED_TARGET";
    /** warning: a by-name lookup that matches an artifact own name but not its registry name. */
    public static final String NAME_MISMATCH_LOOKUP = "NAME_MISMATCH_LOOKUP";
    /** warning: two artifacts with the same own name. */
    public static final String DUPLICATE_NAME = "DUPLICATE_NAME";
    /** error: describing an artifact threw; it is shown with what could be read. */
    public static final String UNDESCRIBABLE = "UNDESCRIBABLE";
    /** info: a registered rule that no rule set or flow references. */
    public static final String UNUSED_RULE = "UNUSED_RULE";
    /** info: no description. */
    public static final String MISSING_DESCRIPTION = "MISSING_DESCRIPTION";
    /** info: no category, in an application where other artifacts have one. Silent until categories are used at all. */
    public static final String UNCATEGORISED = "UNCATEGORISED";

    private ProblemCodes() {
        super();
    }
}
