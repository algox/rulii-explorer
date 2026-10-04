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

import org.rulii.explorer.Explorer;
import org.rulii.explorer.builder.DescriptorBuilder;
import org.rulii.explorer.builder.PlaceholderFilter;
import org.rulii.explorer.descriptor.Descriptor;
import org.rulii.explorer.descriptor.DescriptorJson;
import org.rulii.explorer.expression.ExpressionAnalyzer;
import org.rulii.explorer.problem.ProblemCheck;
import org.rulii.registry.RuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * Builds the descriptor on first request, not at startup (NFR-21), and caches it with its ETag.
 * A build failure is logged and kept as an error payload rather than thrown, so the application
 * is never affected (NFR-22). The cache clears when the context refreshes (devtools restarts).
 *
 * @author Algorithmx Development Team
 * @since 1.0
 *
 */
public class DescriptorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DescriptorService.class);

    /**
     * The cached result: the descriptor and its JSON tree, or the error when the build failed.
     *
     * @param descriptor  the descriptor; null when the build failed.
     * @param json        the descriptor as a JSON tree, nulls omitted (the endpoint body); or the error payload.
     * @param etag        a strong ETag of the JSON body.
     * @param builtAt     when it was built, for {@code Last-Modified}; never part of the body.
     * @param error       what went wrong; null on success.
     */
    public record Snapshot(Descriptor descriptor, JsonNode json, String etag, Instant builtAt, String error) {
        public boolean failed() {
            return error != null;
        }
    }

    private final RuleRegistry registry;
    private final String applicationName;
    private final boolean includeSources;
    private final PlaceholderFilter placeholderValues;
    private final List<ExpressionAnalyzer> analyzers;
    private final List<ProblemCheck> checks;

    private volatile Snapshot snapshot;

    /**
     * @param registry        the registry to describe; must not be null.
     * @param applicationName the application name to report; may be null.
     * @param includeSources  whether to include file, line and class names.
     * @param analyzers       the expression analyzers, in order; the defaults are appended.
     * @param checks          the problem checks; the defaults are appended.
     */
    public DescriptorService(RuleRegistry registry, String applicationName, boolean includeSources,
                             List<ExpressionAnalyzer> analyzers, List<ProblemCheck> checks) {
        this(registry, applicationName, includeSources, null, analyzers, checks);
    }

    /**
     * @param registry          the registry to describe; must not be null.
     * @param applicationName   the application name to report; may be null.
     * @param includeSources    whether to include file, line and class names.
     * @param placeholderValues which placeholder values to show; null shows none.
     * @param analyzers         the expression analyzers, in order; the defaults are appended.
     * @param checks            the problem checks; the defaults are appended.
     */
    public DescriptorService(RuleRegistry registry, String applicationName, boolean includeSources,
                             PlaceholderFilter placeholderValues, List<ExpressionAnalyzer> analyzers, List<ProblemCheck> checks) {
        super();
        this.registry = registry;
        this.applicationName = applicationName;
        this.includeSources = includeSources;
        this.placeholderValues = placeholderValues;
        this.analyzers = List.copyOf(analyzers);
        this.checks = List.copyOf(checks);
    }

    /**
     * The current descriptor, built on the first call.
     *
     * @return the snapshot; never null, {@link Snapshot#failed()} when the build threw.
     */
    public Snapshot snapshot() {
        Snapshot current = snapshot;
        if (current != null) return current;

        synchronized (this) {
            if (snapshot == null) snapshot = build();
            return snapshot;
        }
    }

    /** Drops the cache; the next request rebuilds. */
    public void refresh() {
        snapshot = null;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void onContextRefreshed() {
        refresh();
    }

    private Snapshot build() {
        Instant now = Instant.now();
        try {
            Descriptor descriptor = DescriptorBuilder.of(registry)
                    .applicationName(applicationName)
                    .includeSources(includeSources)
                    .placeholderValues(placeholderValues)
                    .analyzers(analyzers)
                    .checks(checks)
                    .build();
            JsonNode json = DescriptorJson.mapper().valueToTree(descriptor);
            LOGGER.info("rulii explorer described {} artifacts with {} problems", descriptor.artifacts().size(),
                    descriptor.problems().size());
            return new Snapshot(descriptor, json, etag(json), now, null);
        } catch (RuntimeException e) {
            LOGGER.error("rulii explorer could not build the descriptor", e);
            String message = e.getMessage() != null ? e.getClass().getSimpleName() + ": " + e.getMessage() : e.getClass().getName();
            ObjectNode error = DescriptorJson.mapper().createObjectNode();
            error.put("descriptorVersion", Explorer.DESCRIPTOR_VERSION);
            error.putObject("error").put("message", "Could not build the descriptor. " + message);
            return new Snapshot(null, error, etag(error), now, message);
        }
    }

    private static String etag(JsonNode json) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json.toString().getBytes(StandardCharsets.UTF_8));
            return "\"" + HexFormat.of().formatHex(digest, 0, 16) + "\"";
        } catch (NoSuchAlgorithmException e) {
            return "\"" + Integer.toHexString(json.hashCode()) + "\"";
        }
    }
}
