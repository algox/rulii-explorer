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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The {@code rulii.explorer.*} properties (SOLUTION §8.2).
 *
 * <pre>
 * rulii.explorer.enabled=true            # master switch, off by default; the endpoint still needs Actuator exposure
 * rulii.explorer.include-sources=true    # file, line and class names in the descriptor (NFR-4)
 * rulii.explorer.application-name=       # shown in the top bar; defaults to spring.application.name
 * rulii.explorer.ui.enabled=true         # serve the UI
 * rulii.explorer.ui.path=/rulii # where
 * </pre>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
@ConfigurationProperties(prefix = "rulii.explorer")
public class RuliiExplorerProperties {

    /** Master switch, off by default so rules are never shown unless someone chose to. The endpoint still needs {@code management.endpoints.web.exposure.include=rulii}. */
    private boolean enabled = false;

    /** Include file, line and class names in the descriptor. Turn off for deployments that must not reveal them. */
    private boolean includeSources = true;

    /** The application name shown by the explorer. Defaults to {@code spring.application.name}. */
    private String applicationName;

    private final Ui ui = new Ui();

    public RuliiExplorerProperties() {
        super();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isIncludeSources() {
        return includeSources;
    }

    public void setIncludeSources(boolean includeSources) {
        this.includeSources = includeSources;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public Ui getUi() {
        return ui;
    }

    /** The UI settings. */
    public static class Ui {

        /** Serve the single-page app. Turn off to keep the JSON endpoint only. */
        private boolean enabled = false;

        /** Where the UI is served. */
        private String path = "/rulii";

        public Ui() {
            super();
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }
}
