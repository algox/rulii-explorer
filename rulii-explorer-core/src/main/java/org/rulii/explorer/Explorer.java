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
package org.rulii.explorer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Versions the explorer reports about itself: the descriptor contract it produces and the
 * module version it was built from.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class Explorer {

    /** The descriptor contract version. Additive changes keep the major; see the JSON Schema. */
    public static final String DESCRIPTOR_VERSION = "1.1";

    private static final String VERSION = load();

    private Explorer() {
        super();
    }

    /**
     * The explorer module version, as built.
     *
     * @return the Maven project version, or {@code "unknown"} outside a built jar.
     */
    public static String version() {
        return VERSION;
    }

    private static String load() {
        try (InputStream in = Explorer.class.getResourceAsStream("/org/rulii/explorer/rulii-explorer.properties")) {
            if (in == null) return "unknown";
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version", "").strip();
            return version.isEmpty() || version.startsWith("${") ? "unknown" : version;
        } catch (IOException e) {
            return "unknown";
        }
    }
}
