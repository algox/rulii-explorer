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
package org.rulii.explorer.fixture.beans;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The composed constraints of the bean fixture: one flattened into its parts, one reported as a
 * single violation. Annotation types are never beans, so the scan skips them although they
 * carry constraints.
 */
public final class Codes {

    /** Composed, flattened: a violation per part. */
    @NotNull
    @Size(min = 3)
    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidCode {
        String message() default "{validCode}";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    /** Composed, reported as one violation: the parts are hosted. */
    @NotNull
    @Size(min = 3)
    @ReportAsSingleViolation
    @Constraint(validatedBy = {})
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface StrictCode {
        String message() default "strict";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    private Codes() {
        super();
    }
}
