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

import org.rulii.explorer.builder.PlaceholderFilter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code rulii.explorer.*} properties (SOLUTION §8.2).
 *
 * <pre>
 * rulii.explorer.enabled=true            # master switch, off by default; the endpoint still needs Actuator exposure
 * rulii.explorer.include-sources=true    # file, line and class names in the descriptor (NFR-4)
 * rulii.explorer.application-name=       # shown in the top bar; defaults to spring.application.name
 * rulii.explorer.ui.enabled=true         # serve the UI
 * rulii.explorer.ui.path=/rulii          # where
 * rulii.explorer.ui.external-sources=true # the UI may also open another application's descriptor, or a file
 * rulii.explorer.placeholders.show-values=never      # never | always: the values ${key:default} placeholders compiled with
 * rulii.explorer.placeholders.exclude=*password*,... # key globs whose values stay hidden (replaces the default list)
 * rulii.explorer.placeholders.additional-exclude=    # key globs hidden on top of the list above
 * </pre>
 *
 * @author Algorithmx Development Team
 * @since 1.0
 *
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

    private final Placeholders placeholders = new Placeholders();

    public RuliiExplorerProperties() {
        super();
    }

    public Placeholders getPlaceholders() {
        return placeholders;
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

        /**
         * Let the UI show a descriptor from elsewhere: another application's endpoint or a saved
         * JSON by address ({@code ?descriptor=}), or a file from the user's machine. The browser
         * does the reading; nothing goes through this application. Off: the UI shows only its own.
         */
        private boolean externalSources = true;

        public Ui() {
            super();
        }

        public boolean isExternalSources() {
            return externalSources;
        }

        public void setExternalSources(boolean externalSources) {
            this.externalSources = externalSources;
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

    /** Whether to show the values that {@code ${key:default}} placeholders compiled with. */
    public enum ShowValues {
        /** Keys and defaults only, as written. The default. */
        NEVER,
        /** The compiled value of every placeholder whose key is not excluded. */
        ALWAYS
    }

    /**
     * The placeholder settings. Values are the ones the scripts compiled with, read from the
     * compiled script text rulii keeps: what the rules act on, not a lookup at request time.
     */
    public static class Placeholders {

        /** Show the values placeholders compiled with. Off by default: configuration values are not the explorer's to reveal. */
        private ShowValues showValues = ShowValues.NEVER;

        /**
         * Key globs whose values stay hidden ({@code *} any run, {@code ?} one character, case-insensitive,
         * whole key). Setting this replaces the default list; use {@code additional-exclude} to add to it.
         */
        private List<String> exclude = new ArrayList<>(PlaceholderFilter.DEFAULT_EXCLUDES);

        /** Key globs hidden on top of {@code exclude}. */
        private List<String> additionalExclude = new ArrayList<>();

        public Placeholders() {
            super();
        }

        public ShowValues getShowValues() {
            return showValues;
        }

        public void setShowValues(ShowValues showValues) {
            this.showValues = showValues;
        }

        public List<String> getExclude() {
            return exclude;
        }

        public void setExclude(List<String> exclude) {
            this.exclude = exclude;
        }

        public List<String> getAdditionalExclude() {
            return additionalExclude;
        }

        public void setAdditionalExclude(List<String> additionalExclude) {
            this.additionalExclude = additionalExclude;
        }

        /** Every excluded pattern: the list and the additions. */
        public List<String> allExcludes() {
            List<String> all = new ArrayList<>();
            if (exclude != null) all.addAll(exclude);
            if (additionalExclude != null) all.addAll(additionalExclude);
            return all;
        }
    }
}
