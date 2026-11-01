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

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Cascaded into from {@link Order}; cascades into {@link Address}. Carries the two composed
 * constraints of {@link Codes}.
 */
public class Customer {

    @NotBlank
    private String name;

    @Email
    private String email;

    @Valid
    private Address address;

    @Codes.StrictCode(groups = Groups.Basic.class)
    private String code;

    @Codes.ValidCode
    private String ref;

    public Customer() {
        super();
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Address getAddress() {
        return address;
    }

    public String getCode() {
        return code;
    }

    public String getRef() {
        return ref;
    }
}
