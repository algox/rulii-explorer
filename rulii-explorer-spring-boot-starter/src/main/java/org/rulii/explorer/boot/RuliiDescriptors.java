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

import org.rulii.explorer.descriptor.Descriptor;
import org.rulii.explorer.descriptor.DescriptorJson;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The build-time descriptor (FR-44): write the descriptor of a running context to a file from any
 * {@code @SpringBootTest}, without a web layer, so CI can diff it against the previous release.
 *
 * <pre>{@code
 * @SpringBootTest(properties = "rulii.explorer.enabled=true")
 * class DescriptorSnapshotTest {
 *     @Autowired ApplicationContext context;
 *
 *     @Test
 *     void writeDescriptor() throws IOException {
 *         RuliiDescriptors.write(context, Path.of("target/rulii-descriptor.json"));
 *     }
 * }
 * }</pre>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class RuliiDescriptors {

    private RuliiDescriptors() {
        super();
    }

    /**
     * The descriptor of the context, through its {@link DescriptorService}.
     *
     * @param context a context with the explorer auto-configured.
     * @return the descriptor; never null.
     * @throws IllegalStateException when the context has no descriptor service or the build failed.
     */
    public static Descriptor describe(ApplicationContext context) {
        DescriptorService service = context.getBean(DescriptorService.class);
        DescriptorService.Snapshot snapshot = service.snapshot();
        if (snapshot.failed()) throw new IllegalStateException(snapshot.error());
        return snapshot.descriptor();
    }

    /**
     * Writes the descriptor as JSON, byte-identical to {@code GET /actuator/rulii}.
     *
     * @param context a context with the explorer auto-configured.
     * @param path    the file to write; parent directories are created.
     * @throws IOException when the file cannot be written.
     */
    public static void write(ApplicationContext context, Path path) throws IOException {
        DescriptorJson.write(describe(context), path);
    }
}
