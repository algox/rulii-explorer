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
package org.rulii.explorer.boot.ui;

import org.rulii.explorer.Explorer;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * The explorer's {@code index.html}, served with the application's own addresses filled in
 * (SOLUTION §8.1). The page in the UI jar refers to its assets relatively and to the descriptor
 * at the default {@code /actuator/rulii}; this class rewrites both:
 *
 * <ul>
 *   <li>assets move under the versioned path {@code {contextPath}{uiPath}/{version}/}, which
 *   lets them be cached for a year, since every explorer release changes the path;</li>
 *   <li>the {@code rulii-descriptor} meta tag points at the Actuator endpoint, context path and
 *   base path included (NFR-11);</li>
 *   <li>the {@code rulii-sources} meta tag says whether the UI may also show a descriptor from
 *   another address or a file ({@code any}), or only this application's ({@code application}).</li>
 * </ul>
 *
 * @author Max Arulananthan
 * @since 1.0
 */
public final class UiPage {

    /** Where the UI jar keeps the files. Not under {@code META-INF/resources}, so Spring Boot does not serve them on its own. */
    public static final String RESOURCE_ROOT = "META-INF/rulii-explorer/";
    public static final String ASSET_LOCATION = "classpath:/" + RESOURCE_ROOT;
    public static final String DEFAULT_PATH = "/rulii";

    private static final Pattern META = Pattern.compile("<meta name=\"rulii-descriptor\" content=\"[^\"]*\">");
    private static final Pattern SOURCES_META = Pattern.compile("<meta name=\"rulii-sources\" content=\"[^\"]*\">");
    private static final Pattern VERSION_SAFE = Pattern.compile("[A-Za-z0-9._-]+");

    private final String path;
    private final String version;
    private final String segment;
    private final boolean externalSources;
    private final String template;

    /**
     * A page that may also show descriptors from elsewhere.
     *
     * @param uiPath  where the UI is served, e.g. {@code /rulii}; normalised to a leading slash and no trailing slash.
     * @param version the explorer version used in the asset path; sanitised to {@code [A-Za-z0-9._-]}, {@code dev} when unknown.
     */
    public UiPage(String uiPath, String version) {
        this(uiPath, version, true);
    }

    /**
     * @param uiPath          where the UI is served, e.g. {@code /rulii}; normalised to a leading slash and no trailing slash.
     * @param version         the explorer version used in the asset path; sanitised to {@code [A-Za-z0-9._-]}, {@code dev} when unknown.
     * @param externalSources whether the UI may show a descriptor from another address or a file ({@code rulii.explorer.ui.external-sources}).
     */
    public UiPage(String uiPath, String version, boolean externalSources) {
        super();
        this.path = normalisePath(uiPath);
        this.version = version != null && VERSION_SAFE.matcher(version).matches() ? version : "dev";
        this.segment = this.version.endsWith("-SNAPSHOT") || this.version.equals("dev") ? this.version + "-" + fingerprint() : this.version;
        this.externalSources = externalSources;
        this.template = load();
    }

    /** The UI with the explorer's own version. */
    public static UiPage forVersion(String uiPath) {
        return new UiPage(uiPath, Explorer.version());
    }

    /** The UI with the explorer's own version and the external-sources choice. */
    public static UiPage forVersion(String uiPath, boolean externalSources) {
        return new UiPage(uiPath, Explorer.version(), externalSources);
    }

    /** Whether the page may show a descriptor from another address or a file. */
    public boolean externalSources() {
        return externalSources;
    }

    /** The UI path: {@code /rulii}. */
    public String path() {
        return path;
    }

    public String version() {
        return version;
    }

    /**
     * The path segment the assets are served under: the version, plus a fingerprint of the UI jar
     * for snapshot and dev builds, so every rebuild changes the path and no browser keeps stale
     * files for a year.
     */
    public String assetSegment() {
        return segment;
    }

    /** The resource handler pattern for the assets: {@code /rulii/1.0.0/**} ({@code /rulii/1.0.0-SNAPSHOT-k3x9/**} for snapshots). */
    public String assetPattern() {
        return path + "/" + segment + "/**";
    }

    /** The absolute asset base for a request: {@code /app/rulii/1.0.0}. */
    public String assetsBase(String contextPath) {
        return normaliseContext(contextPath) + path + "/" + segment;
    }

    /**
     * Renders the page for one request.
     *
     * @param contextPath    the servlet or management context path, may be empty or null.
     * @param descriptorPath the absolute path of the descriptor endpoint, e.g. {@code /app/actuator/rulii}.
     * @return the HTML.
     */
    public String render(String contextPath, String descriptorPath) {
        String base = assetsBase(contextPath) + "/";
        String html = template.replace("\"./", "\"" + base);
        html = META.matcher(html).replaceFirst("<meta name=\"rulii-descriptor\" content=\"" + descriptorPath + "\">");
        return SOURCES_META.matcher(html).replaceFirst("<meta name=\"rulii-sources\" content=\"" + (externalSources ? "any" : "application") + "\">");
    }

    /** {@code "rulii/"} → {@code "/rulii"}; null or blank → the default. */
    public static String normalisePath(String path) {
        if (path == null || path.isBlank() || path.equals("/")) return DEFAULT_PATH;
        String p = path.trim();
        if (!p.startsWith("/")) p = "/" + p;
        while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    private static String normaliseContext(String contextPath) {
        if (contextPath == null || contextPath.isBlank() || contextPath.equals("/")) return "";
        String c = contextPath.startsWith("/") ? contextPath : "/" + contextPath;
        return c.endsWith("/") ? c.substring(0, c.length() - 1) : c;
    }

    private static String fingerprint() {
        try {
            long modified = new ClassPathResource(RESOURCE_ROOT + "index.html").lastModified();
            return Long.toString(modified / 1000, 36);
        } catch (IOException e) {
            return Long.toString(System.currentTimeMillis() / 1000, 36);
        }
    }

    private static String load() {
        ClassPathResource resource = new ClassPathResource(RESOURCE_ROOT + "index.html");
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("rulii-explorer-ui is not on the classpath: " + RESOURCE_ROOT + "index.html", e);
        }
    }
}
