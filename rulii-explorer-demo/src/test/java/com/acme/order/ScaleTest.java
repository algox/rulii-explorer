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
package com.acme.order;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.rulii.explorer.boot.RuliiDescriptors;
import org.rulii.explorer.descriptor.Descriptor;
import org.rulii.explorer.descriptor.DescriptorJson;
import org.rulii.explorer.descriptor.ProblemSeverity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The scale test (NFR-20, VQ-12, SOLUTION §10): the demo with the {@code scale} profile holds
 * about 1,000 generated artifacts on top of its own. It measures the descriptor build, the
 * endpoint, and the UI in Chromium: first paint of the overview, search latency, the
 * whole-application graph layout and a scripted pan-and-zoom frame rate, and a flowchart.
 * Budgets are generous (CI machines vary); the numbers land in {@code target/scale/results.md}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=scale", "rulii.scale.size=1000", "management.endpoints.web.exposure.include=rulii"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ScaleTest {

    private static final Path OUT = Path.of("target", "scale");
    /**
     * Frame-rate floor for the scripted pan and zoom. Headless Chromium rasterises in software, so
     * the figure measures the machine as much as the app: 42 fps on the development machine, around
     * 25 on a shared CI runner. {@code -Dscale.minFps} lowers the floor there; the measured value is
     * always recorded in {@code results.md}.
     */
    private static final double MIN_FPS = Double.parseDouble(System.getProperty("scale.minFps", "25"));
    private static final String MIN_FPS_TEXT = MIN_FPS == Math.rint(MIN_FPS) ? String.valueOf((int) MIN_FPS) : String.valueOf(MIN_FPS);

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    private final List<String> results = new ArrayList<>();
    private Playwright playwright;
    private Browser browser;

    @BeforeAll
    void start() throws IOException {
        Files.createDirectories(OUT);
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll
    void stop() throws IOException {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
        StringBuilder md = new StringBuilder("# Scale test results\n\n| Measure | Value | Budget |\n|---|---|---|\n");
        for (String r : results) md.append(r).append('\n');
        Files.writeString(OUT.resolve("results.md"), md.toString(), StandardCharsets.UTF_8);
        System.out.println(md);
    }

    private void record(String measure, String value, String budget) {
        results.add("| " + measure + " | " + value + " | " + budget + " |");
    }

    @Test
    @Order(1)
    void descriptorBuildsInTime() throws IOException {
        long t0 = System.nanoTime();
        Descriptor first = RuliiDescriptors.describe(context);
        long coldMs = (System.nanoTime() - t0) / 1_000_000;
        long t1 = System.nanoTime();
        Descriptor warm = RuliiDescriptors.describe(context);
        long warmMs = (System.nanoTime() - t1) / 1_000_000;

        assertTrue(first.artifacts().size() >= 1000, "artifacts: " + first.artifacts().size());
        assertEquals(first, warm, "the build is deterministic");
        String json = DescriptorJson.toJson(first);
        Files.writeString(OUT.resolve("scale.json"), json, StandardCharsets.UTF_8);
        long errors = first.problems().stream().filter(p -> p.severity() == ProblemSeverity.ERROR).count();
        record("Artifacts", first.artifacts().size() + " (" + first.packages().size() + " packages, " + first.references().size() + " references, " + first.problems().size() + " problems, " + errors + " errors)", "≥ 1,000");
        record("Descriptor build, cold", coldMs + " ms", "≤ 5,000 ms");
        record("Descriptor build, warm", warmMs + " ms", "≤ 2,000 ms");
        record("Descriptor JSON", json.length() / 1024 + " KB", "—");
        assertTrue(coldMs <= 5000, "cold build took " + coldMs + " ms");
        assertTrue(warmMs <= 2000, "warm build took " + warmMs + " ms");
    }

    @Test
    @Order(2)
    void endpointAnswersInTime() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/rulii")).build();
        client.send(request, HttpResponse.BodyHandlers.ofString());
        long t0 = System.nanoTime();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertEquals(200, response.statusCode());
        record("GET /actuator/rulii (cached)", ms + " ms, " + response.body().length() / 1024 + " KB", "≤ 1,000 ms");
        assertTrue(ms <= 1000, "endpoint took " + ms + " ms");
    }

    @Test
    @Order(3)
    void uiHoldsUpInTheBrowser() {
        try (BrowserContext ctx = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900))) {
            Page page = ctx.newPage();
            List<String> errors = new ArrayList<>();
            page.onPageError(e -> errors.add(e));
            page.onConsoleMessage(m -> { if ("error".equals(m.type())) errors.add(m.text()); });
            String base = "http://localhost:" + port + "/rulii/";

            long t0 = System.nanoTime();
            page.navigate(base);
            page.waitForSelector(".rx-stats", new Page.WaitForSelectorOptions().setTimeout(30000));
            long overviewMs = (System.nanoTime() - t0) / 1_000_000;
            page.screenshot(new Page.ScreenshotOptions().setPath(OUT.resolve("overview.png")));
            record("Overview first paint (load, index, render)", overviewMs + " ms", "≤ 6,000 ms");

            Object search = page.evaluate("() => { const s = __rx.store.state.search; const qs = ['order', 'total', 'credit limit', 'scaleflow', 'missing', 'fraud', 'region tax', 'validation', 'xyz']; "
                    + "const t0 = performance.now(); let hits = 0; for (let i = 0; i < 20; i++) for (const q of qs) hits += s.query(q).reduce((n, g) => n + g.hits.length, 0); "
                    + "const ms = (performance.now() - t0) / (20 * qs.length); return {avgMs: Math.round(ms * 100) / 100, hits}; }");
            double searchMs = ((Number) ((Map<?, ?>) search).get("avgMs")).doubleValue();
            record("Search, average per query", searchMs + " ms", "≤ 25 ms");

            Map<?, ?> graph = canvas(page, base + "#/graph", ".rx-gnode");
            page.waitForTimeout(300);
            page.screenshot(new Page.ScreenshotOptions().setPath(OUT.resolve("graph-all.png")));
            record("Whole-application graph layout (" + graph.get("nodes") + " nodes, " + graph.get("edges") + " edges)", graph.get("layoutMs") + " ms layout, " + graph.get("renderMs") + " ms render, " + graph.get("elements") + " SVG elements", "≤ 10,000 ms layout");

            Map<?, ?> fps = (Map<?, ?>) page.evaluate("() => new Promise(resolve => { const g = document.querySelector('rx-graph'); const stage = g.stage; const el = g.querySelector('.rx-stage'); "
                    + "const w = el.clientWidth, h = el.clientHeight, ex = stage.extent; const frames = []; const start = performance.now(); let last = start; const seconds = 4; "
                    + "const tick = now => { frames.push(now - last); last = now; const t = (now - start) / 1000; if (t >= seconds) { frames.shift(); const s = [...frames].sort((a, b) => a - b); const q = p => s[Math.min(s.length - 1, Math.floor(s.length * p))]; "
                    + "return resolve({fps: Math.round(frames.length / seconds * 10) / 10, p95Ms: Math.round(q(0.95) * 10) / 10, medianMs: Math.round(q(0.5) * 10) / 10}); } "
                    + "const k = 0.3 + 0.5 * (0.5 + 0.5 * Math.sin(t * 0.9)); const x = w / 2 - ex.width * k / 2 + Math.sin(t * 1.3) * w * 0.35; const y = h / 2 - ex.height * k / 2 + Math.sin(t * 2.6) * h * 0.25; "
                    + "stage.d3.select(el).call(stage.zoom.transform, stage.d3.zoomIdentity.translate(x, y).scale(k)); requestAnimationFrame(tick); }; requestAnimationFrame(tick); })");
            record("Pan and zoom, 4 s scripted (headless Chromium, software rendering)", fps.get("fps") + " fps, median " + fps.get("medianMs") + " ms, p95 " + fps.get("p95Ms") + " ms", "≥ " + MIN_FPS_TEXT + " fps headless; 58 fps on real hardware (spike)");

            Map<?, ?> focus = canvas(page, base + "#/graph?focus=scaleRules0&depth=2", ".rx-gnode");
            page.waitForTimeout(300);
            page.screenshot(new Page.ScreenshotOptions().setPath(OUT.resolve("graph-focus.png")));
            record("Focus graph, depth 2 (" + focus.get("nodes") + " nodes)", focus.get("layoutMs") + " ms layout", "≤ 2,000 ms");

            Map<?, ?> flow = canvas(page, base + "#/ruleflow/scaleFlow0", ".rx-fnode");
            page.waitForTimeout(300);
            page.screenshot(new Page.ScreenshotOptions().setPath(OUT.resolve("flowchart.png")));
            record("Flowchart (" + flow.get("nodes") + " nodes)", flow.get("layoutMs") + " ms layout", "≤ 2,000 ms");

            page.navigate(base + "#/problems");
            page.waitForSelector(".rx-article");
            page.screenshot(new Page.ScreenshotOptions().setPath(OUT.resolve("problems.png")));
            page.navigate(base + "#/ruleflow/scaleFlow0?view=outline");
            page.waitForSelector(".rx-outline");

            assertEquals(List.of(), errors, "console errors");
            assertTrue(overviewMs <= 6000, "overview took " + overviewMs + " ms");
            assertTrue(searchMs <= 25, "search took " + searchMs + " ms per query");
            assertTrue(((Number) graph.get("layoutMs")).longValue() <= 10000, "whole-app layout took " + graph.get("layoutMs") + " ms");
            assertTrue(((Number) focus.get("layoutMs")).longValue() <= 2000, "focus layout took " + focus.get("layoutMs") + " ms");
            assertTrue(((Number) flow.get("layoutMs")).longValue() <= 2000, "flowchart layout took " + flow.get("layoutMs") + " ms");
            assertTrue(((Number) fps.get("fps")).doubleValue() >= MIN_FPS, "frame rate " + fps.get("fps") + " fps (floor " + MIN_FPS_TEXT + ")");
        }
    }

    /** Navigates to a canvas route and returns the rx-canvas-rendered timings. */
    private static Map<?, ?> canvas(Page page, String url, String waitFor) {
        page.evaluate("() => { window.__rxCanvas = new Promise(resolve => document.addEventListener('rx-canvas-rendered', e => resolve(e.detail), {once: true})); }");
        page.navigate(url);
        page.waitForSelector(waitFor, new Page.WaitForSelectorOptions().setTimeout(60000));
        return (Map<?, ?>) page.evaluate("() => window.__rxCanvas");
    }
}
