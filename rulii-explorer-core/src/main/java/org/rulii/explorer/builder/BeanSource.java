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
package org.rulii.explorer.builder;

import org.rulii.explorer.descriptor.Discovery;

import java.util.Map;

/**
 * The beans to describe and the validator that validates them (descriptor 1.1, FR-60). Built with
 * {@code org.rulii.explorer.beans.Beans}; handed to {@link DescriptorBuilder#beans(BeanSource)}.
 *
 * <p>Free of rulii-bean-validation types on purpose: the builder's signature stays loadable when the
 * library is not on the class path. Only the bean describer, loaded when a source is configured,
 * casts {@link #validator()} to the library's {@code BeanValidator}.
 *
 * @author Algorithmx Development Team
 * @since 1.1
 */
public interface BeanSource {

    /**
     * The classes to describe and how each was found. Computed when asked, so a validator's cache is
     * read as it stands; the describer adds the cascade closure itself.
     *
     * @return the classes, in no particular order; never null.
     */
    Map<Class<?>, Discovery> classes();

    /**
     * The validator the beans are validated with: a {@code org.rulii.validation.beans.BeanValidator}.
     *
     * @return the validator; never null.
     */
    Object validator();
}
