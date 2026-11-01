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

import org.junit.jupiter.api.Test;
import org.rulii.explorer.builder.BeanSource;
import org.rulii.explorer.descriptor.Discovery;
import org.rulii.explorer.fixture.beans.Address;
import org.rulii.explorer.fixture.beans.Broken;
import org.rulii.explorer.fixture.beans.Codes;
import org.rulii.explorer.fixture.beans.Customer;
import org.rulii.explorer.fixture.beans.Groups;
import org.rulii.explorer.fixture.beans.Line;
import org.rulii.explorer.fixture.beans.Order;
import org.rulii.explorer.fixture.beans.Plain;
import org.rulii.explorer.fixture.beans.Priced;
import org.rulii.explorer.fixture.beans.Shape;
import org.rulii.explorer.fixture.beans.Staged;
import org.rulii.validation.beans.BeanValidator;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The scan reads class files, not classes: every fixture bean is found, nothing without a
 * declaration is, and the sources combine in the documented order.
 */
class BeanDiscoveryTest {

    private static final String PACKAGE = "org.rulii.explorer.fixture.beans";

    @Test
    void scanFindsTheCandidatesWithoutTheRest() {
        List<Class<?>> found = BeanDiscovery.of(getClass().getClassLoader()).scan(List.of(PACKAGE));

        assertEquals(List.of(Address.class, Broken.class, Customer.class, Line.class, Order.class, Priced.class, Shape.class,
                Shape.Circle.class, Staged.class), found, "sorted by name; Circle through its supertype");
        assertFalse(found.contains(Plain.class), "no declaration");
        assertFalse(found.contains(Groups.class), "no declaration");
        assertFalse(found.contains(Groups.Basic.class), "a group is an empty interface");
        assertFalse(found.contains(Codes.ValidCode.class), "annotation types are never beans");
    }

    @Test
    void candidatesAreReadFromBytes() {
        BeanDiscovery discovery = BeanDiscovery.of(getClass().getClassLoader());

        assertTrue(discovery.isCandidate(Order.class.getName()), "constraints on fields");
        assertTrue(discovery.isCandidate(Line.class.getName()), "constraints on record components");
        assertTrue(discovery.isCandidate(Priced.class.getName()), "a constraint on an interface method");
        assertTrue(discovery.isCandidate(Shape.Circle.class.getName()), "inherited from the superclass");
        assertTrue(discovery.isCandidate(Customer.class.getName()), "a composed constraint, resolved from its own class file");
        assertTrue(discovery.isCandidate(Broken.class.getName()), "@Bind alone");
        assertFalse(discovery.isCandidate(Plain.class.getName()));
        assertFalse(discovery.isCandidate("org.rulii.explorer.fixture.beans.Missing"), "no class file: not a candidate, no error");
        assertFalse(discovery.isCandidate(String.class.getName()), "JDK classes are read like any other, and declare nothing");
    }

    @Test
    void aPluggableSourceListsTheFiles() throws IOException {
        ClassFileSource files = ClassFileSource.of(getClass().getClassLoader());
        List<Integer> sizes = new java.util.ArrayList<>();
        files.read(PACKAGE, bytes -> sizes.add(bytes.length));
        assertTrue(sizes.size() >= 13, "every class file under the package, nested ones included: " + sizes.size());

        BeanDiscovery onlyOrder = new BeanDiscovery(getClass().getClassLoader(), (basePackage, reader) -> {
            try (var in = getClass().getClassLoader().getResourceAsStream(Order.class.getName().replace('.', '/') + ".class")) {
                reader.accept(in.readAllBytes());
            }
        });
        assertEquals(List.of(Order.class), onlyOrder.scan(List.of(PACKAGE)));
    }

    @Test
    void sourcesCombineConfiguredFirst() {
        BeanValidator validator = BeanValidator.builder().build();
        validator.getBeanDefinition(Staged.class);
        validator.getBeanDefinition(Plain.class);

        BeanSource source = Beans.of(validator)
                .scan(List.of(PACKAGE), getClass().getClassLoader())
                .classes(List.of(Order.class, Plain.class))
                .build();
        Map<Class<?>, Discovery> classes = source.classes();

        assertSame(validator, source.validator());
        assertEquals(Discovery.CONFIGURED, classes.get(Order.class), "configured wins over scanned");
        assertEquals(Discovery.CONFIGURED, classes.get(Plain.class), "configured wins over validated");
        assertEquals(Discovery.SCAN, classes.get(Customer.class));
        assertEquals(Discovery.SCAN, classes.get(Staged.class), "scanned wins over validated");
        assertFalse(classes.containsKey(Address.class) && classes.get(Address.class) == Discovery.CASCADE, "the closure is the describer's");

        Map<Class<?>, Discovery> validatedOnly = Beans.of(validator).build().classes();
        assertEquals(Set.of(Staged.class, Plain.class), validatedOnly.keySet());
        assertEquals(Discovery.VALIDATED, validatedOnly.get(Staged.class));

        assertTrue(Beans.of(validator).validated(false).build().classes().isEmpty());
    }
}
