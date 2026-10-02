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
package org.rulii.explorer.ui;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.ConsoleMessage;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.ColorScheme;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives the UI in a real browser (SOLUTION §10): the in-browser unit tests in
 * {@code src/test/resources/browser}, then every screen and state in light and dark, as
 * screenshots under {@code target/screens} compared with the baselines in
 * {@code src/test/resources/screens} when present ({@code -Dscreens.update=true} rewrites them).
 *
 * <p>The descriptor is the demo application's golden file, served by a small JDK HTTP server
 * together with the UI files, so no Spring Boot is involved and the pages are deterministic.
 */
class BrowserTest {

    private static final Path UI = Path.of("src", "main", "resources", "META-INF", "rulii-explorer");
    private static final Path TESTS = Path.of("src", "test", "resources", "browser");
    private static final Path GOLDEN = Path.of("..", "rulii-explorer-demo", "src", "test", "resources", "golden", "order-service.json");
    private static final Path OUT = Path.of("target", "screens");
    private static final Path BASELINES = Path.of("src", "test", "resources", "screens");
    private static final Path AXE = Path.of("src", "test", "resources", "vendor", "axe", "axe.min.js");

    private static HttpServer server;
    private static String base;
    private static Playwright playwright;
    private static Browser browser;
    private static String golden;

    @BeforeAll
    static void start() throws IOException {
        golden = Files.readString(GOLDEN);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", BrowserTest::handle);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
        Files.createDirectories(OUT);
    }

    @AfterAll
    static void stop() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
        if (server != null) server.stop(0);
    }

    @Test
    void unitTestsPassInTheBrowser() {
        try (BrowserContext context = browser.newContext(); ) {
            Page page = context.newPage();
            List<String> errors = watch(page);
            page.navigate(base + "/test/runner.html");
            page.waitForFunction("() => window.__rxDone === true");
            @SuppressWarnings("unchecked")
            Map<String, Object> results = (Map<String, Object>) page.evaluate("() => window.__rxResults");
            @SuppressWarnings("unchecked")
            List<String> failures = (List<String>) results.get("failures");
            assertEquals(List.of(), failures, "browser unit tests failed");
            assertEquals(List.of(), errors, "console errors");
            assertEquals(0, ((Number) results.get("failed")).intValue(), "failed browser tests");
            assertTrue(((Number) results.get("passed")).intValue() >= 17, "ran " + results.get("passed") + " tests");
        }
    }

    @Test
    void keyboardOnlyPath() {
        try (BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900))) {
            Page page = context.newPage();
            List<String> errors = watch(page);
            page.navigate(base + "/case/ok/#/");
            page.waitForSelector(".rx-stats");
            // The palette from the keyboard: open, type, move, open the result
            page.keyboard().press("Control+k");
            page.waitForSelector(".rx-palette[open]");
            page.keyboard().type("min total");
            page.waitForSelector(".rx-option");
            page.keyboard().press("ArrowDown");
            page.keyboard().press("ArrowUp");
            page.keyboard().press("Enter");
            page.waitForSelector(".rx-summary");
            assertTrue(page.url().contains("#/rule/MinTotalRule"), page.url());
            assertEquals("hidden", page.evaluate("() => document.querySelector('.rx-palette').open ? 'open' : 'hidden'"));
            // Escape closes the palette without navigating
            page.keyboard().press("Control+k");
            page.waitForSelector(".rx-palette[open]");
            page.keyboard().press("Escape");
            page.waitForFunction("() => !document.querySelector('.rx-palette').open");
            // The help sheet: ? opens it, Escape closes it, and the palette footer opens it too
            page.keyboard().press("?");
            page.waitForSelector(".rx-help[open]");
            page.keyboard().press("Escape");
            page.waitForFunction("() => !document.querySelector('.rx-help').open");
            page.keyboard().press("Control+k");
            page.waitForSelector(".rx-palette[open]");
            page.click(".rx-palette-help");
            page.waitForSelector(".rx-help[open]");
            page.waitForFunction("() => !document.querySelector('.rx-palette').open");
            page.click(".rx-help button[aria-label='Close help']");
            page.waitForFunction("() => !document.querySelector('.rx-help').open");
            // F fits a flowchart from the keyboard: the zoom changes from the fitted value after zooming in
            page.navigate(base + "/case/ok/#/ruleflow/orderProcessingFlow");
            page.waitForSelector(".rx-fnode");
            page.click(".rx-zoom button[aria-label='Zoom in']");
            page.click(".rx-zoom button[aria-label='Zoom in']");
            String zoomed = (String) page.evaluate("() => document.querySelector('.rx-zoom-pct').textContent");
            page.focus("main");
            page.keyboard().press("f");
            page.waitForFunction("z => document.querySelector('.rx-zoom-pct').textContent !== z", zoomed);
            // Tab reaches the sidebar, the actions and the content, and every stop is visible
            page.navigate(base + "/case/ok/#/ruleflow/orderProcessingFlow?view=outline");
            page.waitForSelector(".rx-outline");
            int stops = 0;
            String first = null;
            for (int i = 0; i < 60; i++) {
                page.keyboard().press("Tab");
                String id = (String) page.evaluate("() => { const e = document.activeElement; if (!e || e === document.body) return 'body'; const r = e.getBoundingClientRect(); return (r.width > 0 && r.height > 0 ? '' : 'INVISIBLE ') + e.tagName + '.' + e.className; }");
                assertFalse(id.startsWith("INVISIBLE"), "tab stop " + i + " is not visible: " + id);
                if (first == null) first = id;
                if (!"body".equals(id)) stops++;
            }
            assertTrue(stops >= 40, "keyboard reaches the page: " + stops + " stops");
            // A graph node is reachable and selectable from the keyboard
            page.navigate(base + "/case/ok/#/graph?focus=pricingRules");
            page.waitForSelector(".rx-gnode");
            page.focus(".rx-gnode[data-id='FreeShippingRule']");
            page.keyboard().press("Enter");
            page.waitForFunction("() => location.hash.includes('selected=FreeShippingRule')");
            assertEquals(List.of(), errors, "console errors");
        }
    }

    /** One test per screen and theme, so a failure names the screen. */
    @TestFactory
    Stream<DynamicTest> screens() {
        return Stream.of(
                screen("overview", "ok", "", ".rx-stats", true),
                screen("rule", "ok", "/rule/MinTotalRule", ".rx-summary", true),
                screen("rule-raw", "ok", "/rule/MinTotalRule", ".rx-summary", false, p -> { p.click(".rx-seg button:nth-child(2)"); p.waitForSelector(".rx-raw-grid"); }),
                screen("validator", "ok", "/rule/EmailFormatRule", ".rx-summary", false),
                screen("compiled", "ok", "/rule/fraudScoreRule", ".rx-signature", false),
                screen("rule-js", "ok", "/rule/LoyaltyPointsRule", ".rx-summary", true),
                screen("ruleset-js", "ok", "/ruleset/loyaltyRules", ".rx-members", false),
                screen("ruleset", "ok", "/ruleset/orderValidationRules", ".rx-members", true),
                screen("flow-outline", "ok", "/ruleflow/orderProcessingFlow?view=outline", ".rx-outline", true),
                screen("flow-step", "ok", "/ruleflow/nightlyRepriceFlow?view=outline&step=commands%5B1%5D.body%5B1%5D", ".rx-step[aria-selected]", false),
                screen("flowchart", "ok", "/ruleflow/orderProcessingFlow", ".rx-fnode", true),
                screen("flowchart-step", "ok", "/ruleflow/orderProcessingFlow?view=flowchart&step=commands%5B1%5D", ".rx-fnode-selected", false),
                screen("flowchart-nightly", "ok", "/ruleflow/nightlyRepriceFlow", ".rx-fnode", false),
                screen("flowchart-js", "ok", "/ruleflow/loyaltyFlow", ".rx-fnode", false),
                screen("graph-focus", "ok", "/graph?focus=orderValidationRules", ".rx-gnode", true),
                screen("graph-focus-flow", "ok", "/graph?focus=orderProcessingFlow&depth=2", ".rx-gnode", false),
                screen("graph-all", "ok", "/graph", ".rx-gnode", true),
                screen("graph-all-selected", "ok", "/graph?selected=nightlyRepriceFlow", ".rx-gaside", false),
                screen("binding", "ok", "/binding/order", ".rx-xref", false),
                screen("package", "ok", "/package/rules/order", ".rx-rows", false),
                screen("problems", "ok", "/problems", ".rx-article", true),
                screen("search", "ok", "", ".rx-stats", true, p -> { p.keyboard().press("Control+k"); p.waitForSelector(".rx-palette[open]"); p.keyboard().type("total"); p.waitForSelector(".rx-option"); }),
                screen("help", "ok", "", ".rx-stats", true, p -> { p.keyboard().press("?"); p.waitForSelector(".rx-help[open]"); }),
                screen("loading", "slow", "", ".rx-progress", false),
                screen("state-empty", "empty", "", ".rx-state", false),
                screen("state-not-exposed", "notexposed", "", ".rx-state", false),
                screen("state-sign-in", "unauthorized", "", ".rx-state", false),
                screen("state-forbidden", "forbidden", "", ".rx-state", false),
                screen("state-failed", "failed", "", ".rx-state", false),
                screen("state-partial", "partial", "", ".rx-note-warning", false),
                screen("state-partial-rule", "partial", "/rule/StockAvailableRule", ".rx-undescribed-card", false),
                screen("missing", "ok", "/rule/Nope", ".rx-state", false)
        ).flatMap(s -> s);
    }

    private interface Action {
        void run(Page page);
    }

    private Stream<DynamicTest> screen(String name, String scenario, String route, String waitFor, boolean dark) {
        return screen(name, scenario, route, waitFor, dark, null);
    }

    private Stream<DynamicTest> screen(String name, String scenario, String route, String waitFor, boolean dark, Action action) {
        List<DynamicTest> tests = new ArrayList<>();
        tests.add(DynamicTest.dynamicTest(name + " (light)", () -> capture(name + "-light", scenario, route, waitFor, false, action)));
        if (dark) tests.add(DynamicTest.dynamicTest(name + " (dark)", () -> capture(name + "-dark", scenario, route, waitFor, true, action)));
        return tests.stream();
    }

    private void capture(String file, String scenario, String route, String waitFor, boolean dark, Action action) throws IOException {
        try (BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1440, 900).setDeviceScaleFactor(1).setColorScheme(dark ? ColorScheme.DARK : ColorScheme.LIGHT))) {
            Page page = context.newPage();
            List<String> errors = watch(page);
            page.navigate(base + "/case/" + scenario + "/#" + route);
            page.waitForSelector(waitFor, new Page.WaitForSelectorOptions().setTimeout(15000));
            if (action != null) action.run(page);
            page.evaluate("() => document.fonts.ready");
            page.waitForTimeout(150);
            Path actual = OUT.resolve(file + ".png");
            page.screenshot(new Page.ScreenshotOptions().setPath(actual));
            assertEquals(List.of(), errors, file + ": console errors");
            compareWithBaseline(file, actual);
            assertEquals(List.of(), accessibilityViolations(page), file + ": accessibility violations (serious or critical)");
        }
    }

    private static void compareWithBaseline(String file, Path actual) throws IOException {
        Path baseline = BASELINES.resolve(file + ".png");
        if (Boolean.getBoolean("screens.update")) {
            Files.createDirectories(BASELINES);
            Files.copy(actual, baseline, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        if (!Files.exists(baseline)) return;
        double differing = Screens.differingFraction(baseline, actual);
        assertTrue(differing <= 0.005, file + ": " + String.format("%.2f%%", differing * 100) + " of pixels differ from the baseline (" + actual + ")");
    }

    /**
     * Runs axe-core (WCAG 2.1 A and AA rules) on the current page and returns the serious and
     * critical violations as readable lines (NFR-31). Minor and moderate ones are printed.
     */
    @SuppressWarnings("unchecked")
    static List<String> accessibilityViolations(Page page) {
        page.addScriptTag(new Page.AddScriptTagOptions().setPath(AXE));
        List<Map<String, Object>> violations = (List<Map<String, Object>>) page.evaluate(
                "async () => { const r = await axe.run(document, {runOnly: {type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']}}); "
                + "return r.violations.map(v => ({id: v.id, impact: v.impact, help: v.help, nodes: v.nodes.slice(0, 4).map(n => n.target.join(' ') + (n.failureSummary ? ' :: ' + n.failureSummary.slice(0, 160) : ''))})); }");
        List<String> serious = new ArrayList<>();
        for (Map<String, Object> v : violations) {
            String line = v.get("id") + " [" + v.get("impact") + "] " + v.get("help") + " -> " + v.get("nodes");
            if ("serious".equals(v.get("impact")) || "critical".equals(v.get("impact"))) serious.add(line);
            else System.out.println("axe (" + v.get("impact") + "): " + line);
        }
        return serious;
    }

    /** Collects console errors and uncaught exceptions; expected failed loads of the descriptor are not errors of the UI. */
    private static List<String> watch(Page page) {
        List<String> errors = new CopyOnWriteArrayList<>();
        page.onConsoleMessage((ConsoleMessage m) -> {
            if ("error".equals(m.type()) && !m.text().contains("Failed to load resource")) errors.add(m.text());
        });
        page.onPageError(e -> errors.add("pageerror: " + e));
        return errors;
    }

    // ── The test server ──────────────────────────────────────────────────────

    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        try {
            if (path.startsWith("/test/")) { file(exchange, TESTS.resolve(path.substring("/test/".length()))); return; }
            if (path.equals("/actuator/rulii")) { send(exchange, 200, "application/json", golden); return; }
            if (path.startsWith("/rulii-explorer/")) { file(exchange, UI.resolve(path.substring("/rulii-explorer/".length()))); return; }
            if (path.startsWith("/case/")) {
                String[] parts = path.substring("/case/".length()).split("/", 2);
                String scenario = parts[0];
                String rest = parts.length > 1 ? parts[1] : "";
                if (rest.equals("descriptor")) { descriptor(exchange, scenario); return; }
                if (rest.isEmpty() || rest.equals("index.html")) {
                    String html = Files.readString(UI.resolve("index.html")).replace("content=\"/actuator/rulii\"", "content=\"/case/" + scenario + "/descriptor\"");
                    send(exchange, 200, "text/html; charset=utf-8", html);
                    return;
                }
                file(exchange, UI.resolve(rest));
                return;
            }
            send(exchange, 404, "text/plain", "not found");
        } catch (RuntimeException e) {
            send(exchange, 500, "text/plain", e.toString());
        }
    }

    private static void descriptor(HttpExchange exchange, String scenario) throws IOException {
        switch (scenario) {
            case "empty" -> send(exchange, 200, "application/json",
                    "{\"descriptorVersion\":\"1.0\",\"application\":{\"name\":\"order-service\",\"ruliiVersion\":\"2.1.0\",\"explorerVersion\":\"1.0.0\"},\"packages\":[],\"artifacts\":[],\"references\":[],\"bindings\":[],\"problems\":[]}");
            case "notexposed" -> send(exchange, 404, "application/json", "{\"timestamp\":\"now\",\"status\":404,\"error\":\"Not Found\"}");
            case "unauthorized" -> send(exchange, 401, "text/plain", "");
            case "forbidden" -> send(exchange, 403, "text/plain", "");
            case "failed" -> send(exchange, 500, "application/json",
                    "{\"descriptorVersion\":\"1.0\",\"error\":{\"message\":\"Could not build the descriptor. IllegalStateException: Rule 'StockAvailableRule' has no when method\"}}");
            case "slow" -> {
                try { Thread.sleep(4000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                send(exchange, 200, "application/json", golden);
            }
            case "partial" -> send(exchange, 200, "application/json", golden.replace("\"problems\": [",
                    "\"problems\": [ {\"severity\": \"error\", \"code\": \"UNDESCRIBABLE\", \"artifact\": \"StockAvailableRule\", \"message\": \"IllegalStateException: Could not read the rule definition of bean 'StockAvailableRule'\"},"));
            default -> send(exchange, 200, "application/json", golden);
        }
    }

    private static void file(HttpExchange exchange, Path file) throws IOException {
        if (!Files.isRegularFile(file)) { send(exchange, 404, "text/plain", "not found: " + file); return; }
        byte[] bytes = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", contentType(file.toString()));
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
    }

    private static void send(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) { if (bytes.length > 0) out.write(bytes); }
    }

    private static String contentType(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".json")) return "application/json";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".woff2")) return "font/woff2";
        if (name.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }
}
