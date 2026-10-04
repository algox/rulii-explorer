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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;
import org.rulii.explorer.builder.DescriptorBuilder;
import org.rulii.explorer.fixture.OrderFixture;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The JSON form is stable, valid against the published schema, round-trips, and matches the
 * golden file built from the Java fixture. Run with {@code -Dgolden.update=true} to rewrite
 * the golden file after an intended change, then review the diff.
 */
class DescriptorJsonTest {

    private static final Path GOLDEN = Path.of("src/test/resources/golden/java-fixture.json");

    private static Descriptor build() {
        return DescriptorBuilder.of(new OrderFixture().registry())
                .applicationName("order-service")
                .ruliiVersion("2.1.0")
                .analyzers(List.of(OrderFixture.stubAnalyzer()))
                .includeSources(false) // line numbers of the fixture would change with every edit
                .build();
    }

    @Test
    void jsonIsStableAndOrdered() {
        String first = DescriptorJson.toJson(build());
        String second = DescriptorJson.toJson(build());
        assertEquals(first, second, "two builds of the same registry give the same bytes");
        assertTrue(first.startsWith("{\n  \"descriptorVersion\": \"1.1\",\n  \"application\": {"), first.substring(0, 80));
        assertFalse(first.contains("\r"), "line ends are \\n on every platform");
        assertTrue(first.endsWith("}\n"));
        assertFalse(first.contains(": null"), "nulls are omitted");
    }

    @Test
    void roundTrips() {
        Descriptor descriptor = build();
        Descriptor back = DescriptorJson.fromJson(DescriptorJson.toJson(descriptor));
        assertEquals(descriptor, back);
    }

    @Test
    void matchesTheGoldenFileAndTheSchema() throws IOException {
        String json = DescriptorJson.toJson(build());
        assertNoSchemaErrors(json);

        if (Boolean.getBoolean("golden.update") || !Files.exists(GOLDEN)) {
            Files.createDirectories(GOLDEN.getParent());
            Files.writeString(GOLDEN, json, StandardCharsets.UTF_8);
            fail("Golden file written to " + GOLDEN.toAbsolutePath() + "; review it and run again.");
        }

        String golden = Files.readString(GOLDEN, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertEquals(golden, json, "descriptor differs from the golden file; rerun with -Dgolden.update=true if intended");
        assertNoSchemaErrors(golden);
    }

    private static void assertNoSchemaErrors(String json) throws IOException {
        try (InputStream schemaStream = DescriptorJsonTest.class.getResourceAsStream("/rulii-descriptor-1.schema.json")) {
            assertNotNull(schemaStream, "schema resource");
            JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaStream);
            Set<ValidationMessage> errors = schema.validate(new ObjectMapper().readTree(json));
            assertTrue(errors.isEmpty(), errors.toString());
        }
    }
}
