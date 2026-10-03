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

import org.rulii.explorer.descriptor.Descriptor;
import org.rulii.explorer.expression.ExpressionAnalyzer;
import org.rulii.explorer.expression.ExpressionAnalyzers;
import org.rulii.explorer.problem.ProblemCheck;
import org.rulii.explorer.problem.ProblemChecks;
import org.rulii.registry.RuleRegistry;
import org.rulii.rule.Rule;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds the {@link Descriptor} of everything a {@link RuleRegistry} holds.
 *
 * <pre>{@code
 * Descriptor descriptor = DescriptorBuilder.of(registry)
 *         .applicationName("order-service")
 *         .analyzers(List.of(new SpelExpressionAnalyzer()))
 *         .build();
 * }</pre>
 *
 * <p>Nothing runs: only definitions are read. Describing the registry does instantiate lazy
 * artifacts, which is why the Spring Boot starter builds on first request, not at startup.
 *
 * @author Max Arulananthan
 * @since 1.0
 *
 */
public final class DescriptorBuilder {

    private final RuleRegistry registry;
    private String applicationName;
    private String ruliiVersion = ruliiVersionFromManifest();
    private ExpressionAnalyzers analyzers = ExpressionAnalyzers.defaults();
    private List<ProblemCheck> checks = ProblemChecks.defaults();
    private boolean includeSources = true;
    private PlaceholderFilter placeholderValues;

    private DescriptorBuilder(RuleRegistry registry) {
        super();
        this.registry = Objects.requireNonNull(registry, "registry cannot be null.");
    }

    /**
     * @param registry the registry to describe; must not be null.
     * @return a builder.
     */
    public static DescriptorBuilder of(RuleRegistry registry) {
        return new DescriptorBuilder(registry);
    }

    /** The application name shown in the descriptor; null when unknown. */
    public DescriptorBuilder applicationName(String applicationName) {
        this.applicationName = applicationName;
        return this;
    }

    /** The rulii version to report. Defaults to the rulii jar manifest, or {@code "unknown"}. */
    public DescriptorBuilder ruliiVersion(String ruliiVersion) {
        this.ruliiVersion = ruliiVersion != null ? ruliiVersion : "unknown";
        return this;
    }

    /** The expression analyzers, tried in order per script language. Default: {@link ExpressionAnalyzers#defaults()}, SpEL only. */
    public DescriptorBuilder analyzers(List<ExpressionAnalyzer> analyzers) {
        this.analyzers = new ExpressionAnalyzers(analyzers);
        return this;
    }

    /** The problem checks to run. Default: {@link ProblemChecks#defaults()}. */
    public DescriptorBuilder checks(List<ProblemCheck> checks) {
        this.checks = checks == null ? List.of() : new ArrayList<>(checks);
        return this;
    }

    /** Whether to include file, line and class names (NFR-4). Default: true. */
    public DescriptorBuilder includeSources(boolean includeSources) {
        this.includeSources = includeSources;
        return this;
    }

    /**
     * Shows the values the scripts' {@code ${key:default}} placeholders compiled with, for the keys
     * the filter lets through; the others are reported as hidden. The values come from the compiled
     * script text rulii keeps, never from a lookup, so they are the ones the rules act on. Default:
     * null, no values (keys and defaults only).
     *
     * @param filter which values to show, such as {@link PlaceholderFilter#excluding(java.util.Collection)}
     *               with {@link PlaceholderFilter#DEFAULT_EXCLUDES}; null shows none.
     */
    public DescriptorBuilder placeholderValues(PlaceholderFilter filter) {
        this.placeholderValues = filter;
        return this;
    }

    /**
     * Describes the registry.
     *
     * @return the descriptor; never null.
     */
    public Descriptor build() {
        return new Describer(registry, analyzers, placeholderValues, includeSources, applicationName, ruliiVersion, checks).describe();
    }

    private static String ruliiVersionFromManifest() {
        Package rulii = Rule.class.getPackage();
        String version = rulii != null ? rulii.getImplementationVersion() : null;
        return version != null && !version.isBlank() ? version : "unknown";
    }
}
