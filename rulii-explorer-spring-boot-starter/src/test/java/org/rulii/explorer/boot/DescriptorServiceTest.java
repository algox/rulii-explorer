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
package org.rulii.explorer.boot;

import org.junit.jupiter.api.Test;
import org.rulii.explorer.descriptor.Descriptor;
import org.rulii.explorer.descriptor.DescriptorJson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * No web layer: the service caches, refreshes, hides sources on request, and
 * {@link RuliiDescriptors} writes the build-time descriptor (FR-44).
 *
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"rulii.explorer.include-sources=false", "rulii.explorer.application-name=named-by-property"})
class DescriptorServiceTest {

    @Autowired
    private DescriptorService service;

    @Autowired
    private ConfigurableApplicationContext context;

    @Test
    void buildsOnceAndCaches() {
        DescriptorService.Snapshot first = service.snapshot();
        assertFalse(first.failed());
        assertSame(first, service.snapshot(), "built on first request, then cached");
        assertNotNull(first.etag());
        assertTrue(first.etag().startsWith("\""));

        service.refresh();
        DescriptorService.Snapshot second = service.snapshot();
        assertNotSame(first, second);
        assertEquals(first.descriptor(), second.descriptor());
        assertEquals(first.etag(), second.etag(), "same content, same ETag");

        context.publishEvent(new ContextRefreshedEvent(context));
        assertNotSame(second, service.snapshot(), "a context refresh clears the cache");
    }

    @Test
    void honoursTheProperties() {
        Descriptor descriptor = service.snapshot().descriptor();
        assertEquals("named-by-property", descriptor.application().name());
        assertTrue(descriptor.artifacts().stream().allMatch(a -> a.source() == null), "include-sources=false");
        assertFalse(descriptor.application().placeholderValues(), "placeholder values are off unless asked for");
    }

    @Test
    void writesTheBuildTimeDescriptor() throws IOException {
        Path file = Path.of("target", "rulii-descriptor-test.json");
        Files.deleteIfExists(file);

        RuliiDescriptors.write(context, file);

        String json = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(json.startsWith("{\n  \"descriptorVersion\": \"1.1\""), json.substring(0, 60));
        assertEquals(service.snapshot().descriptor(), DescriptorJson.fromJson(json));
    }
}
