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

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The descriptor's JSON form: pretty printed with {@code \n} line ends, keys in declaration
 * order, nulls omitted, map keys sorted. The same bytes on every platform, so golden files and
 * release diffs are clean (FR-43).
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
public final class DescriptorJson {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            // Settings values read back as the same Java types they were written with.
            .enable(DeserializationFeature.USE_LONG_FOR_INTS)
            .defaultPrettyPrinter(new DefaultPrettyPrinter()
                    .withSeparators(Separators.createDefaultInstance()
                            .withObjectNameValueSpacing(Separators.Spacing.AFTER))
                    .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                    .withArrayIndenter(new DefaultIndenter("  ", "\n")))
            .build();

    private DescriptorJson() {
        super();
    }

    /** The mapper configured for descriptors. Use it to serialise a descriptor into any Jackson sink. */
    public static JsonMapper mapper() {
        return MAPPER;
    }

    /**
     * @param descriptor the descriptor.
     * @return its JSON, pretty printed, ending with a newline.
     */
    public static String toJson(Descriptor descriptor) {
        return MAPPER.writeValueAsString(descriptor) + "\n";
    }

    /**
     * @param json descriptor JSON.
     * @return the descriptor.
     */
    public static Descriptor fromJson(String json) {
        return MAPPER.readValue(json, Descriptor.class);
    }

    /**
     * Writes the descriptor as UTF-8 JSON, creating parent directories as needed.
     *
     * @param descriptor the descriptor.
     * @param path       the file to write.
     * @throws IOException when the file cannot be written.
     */
    public static void write(Descriptor descriptor, Path path) throws IOException {
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Files.writeString(path, toJson(descriptor), StandardCharsets.UTF_8);
    }
}
