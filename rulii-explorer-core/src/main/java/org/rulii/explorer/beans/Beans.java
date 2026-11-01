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
package org.rulii.explorer.beans;

import org.rulii.explorer.builder.BeanSource;
import org.rulii.explorer.descriptor.Discovery;
import org.rulii.validation.beans.BeanValidator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the {@link BeanSource} the descriptor builder describes beans from: which classes, found
 * how, validated by which {@link BeanValidator}.
 *
 * <pre>{@code
 * BeanSource beans = Beans.of(validator)
 *         .scan(List.of("com.acme.order"), loader)
 *         .classes(List.of(LegacyOrder.class))
 *         .build();
 * Descriptor descriptor = DescriptorBuilder.of(registry).beans(beans).build();
 * }</pre>
 *
 * <p>The source lists its classes when asked, so each descriptor build sees the validator's cache
 * as it stands. A class named more than one way keeps the first of: configured, scanned,
 * validated. The cascade closure is the describer's.
 *
 * <p>The only public entry of the explorer that names a rulii-bean-validation type.
 *
 * @author Algorithmx Development Team
 * @since 1.1
 */
public final class Beans {

    private final BeanValidator validator;
    private final List<String> packages = new ArrayList<>();
    private ClassLoader loader;
    private ClassFileSource files;
    private final List<Class<?>> classes = new ArrayList<>();
    private boolean validated = true;

    private Beans(BeanValidator validator) {
        super();
        this.validator = Objects.requireNonNull(validator, "validator cannot be null.");
    }

    /**
     * @param validator the validator the beans are validated with; must not be null.
     * @return a builder.
     */
    public static Beans of(BeanValidator validator) {
        return new Beans(validator);
    }

    /**
     * Scans packages for candidate classes (discovery {@code scan}), reading class files through
     * the loader's resources; see {@link BeanDiscovery}.
     *
     * @param packages base packages, sub-packages included; empty scans nothing.
     * @param loader   the loader that lists and loads them; must not be null.
     */
    public Beans scan(Collection<String> packages, ClassLoader loader) {
        this.packages.addAll(packages);
        this.loader = Objects.requireNonNull(loader, "loader cannot be null.");
        return this;
    }

    /**
     * Where the scan reads class files from, in place of the loader's own resources: the Spring
     * Boot starter passes Spring's resolver so nested Boot jars work. Default: {@link ClassFileSource#of(ClassLoader)}.
     *
     * @param files the class files; null for the default.
     */
    public Beans files(ClassFileSource files) {
        this.files = files;
        return this;
    }

    /**
     * Classes to describe whether or not the scan finds them (discovery {@code configured}). A
     * class that declares nothing the library reads is described empty, with a problem.
     *
     * @param classes the classes.
     */
    public Beans classes(Collection<Class<?>> classes) {
        this.classes.addAll(classes);
        return this;
    }

    /**
     * Whether to also describe what the validator has already read (discovery {@code validated}):
     * {@link BeanValidator#getBeanClasses()} at the time of each build. Default: true.
     */
    public Beans validated(boolean validated) {
        this.validated = validated;
        return this;
    }

    /**
     * @return the source; never null.
     */
    public BeanSource build() {
        BeanDiscovery discovery = packages.isEmpty() ? null
                : files != null ? new BeanDiscovery(loader, files) : BeanDiscovery.of(loader);
        return new Source(validator, List.copyOf(packages), discovery, List.copyOf(classes), validated);
    }

    private static final class Source implements BeanSource {

        private final BeanValidator validator;
        private final List<String> packages;
        private final BeanDiscovery discovery;
        private final List<Class<?>> classes;
        private final boolean validated;

        private Source(BeanValidator validator, List<String> packages, BeanDiscovery discovery, List<Class<?>> classes, boolean validated) {
            super();
            this.validator = validator;
            this.packages = packages;
            this.discovery = discovery;
            this.classes = classes;
            this.validated = validated;
        }

        @Override
        public Map<Class<?>, Discovery> classes() {
            Map<Class<?>, Discovery> result = new LinkedHashMap<>();
            for (Class<?> type : classes) {
                result.putIfAbsent(type, Discovery.CONFIGURED);
            }
            if (discovery != null) {
                for (Class<?> type : discovery.scan(packages)) {
                    result.putIfAbsent(type, Discovery.SCAN);
                }
            }
            if (validated) {
                List<Class<?>> known = new ArrayList<>(validator.getBeanClasses());
                known.sort((a, b) -> a.getName().compareTo(b.getName()));
                for (Class<?> type : known) {
                    result.putIfAbsent(type, Discovery.VALIDATED);
                }
            }
            return result;
        }

        @Override
        public Object validator() {
            return validator;
        }

        @Override
        public String toString() {
            return "Beans{packages=" + packages + ", classes=" + classes.size() + ", validated=" + validated + "}";
        }
    }
}
