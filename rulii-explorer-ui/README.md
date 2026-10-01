# rulii-explorer-ui

The explorer's single-page app, shipped as static files under
`src/main/resources/META-INF/rulii-explorer/`. There is no build step: the files are served as
written, with Lit, d3, ELK and the fonts copied into `vendor/` (SOLUTION.md §9 and §14).

The files are deliberately *not* under `META-INF/resources/`, so Spring Boot does not serve them
on its own. The starter serves them: the page at `rulii.explorer.ui.path` (default
`/rulii-explorer`) with the descriptor address filled in, and the assets under
`/rulii-explorer/{explorer version}/` with a year of caching. Opening `index.html` from any plain
static server also works; it then expects the descriptor at `/actuator/rulii`.

## Layout

```
META-INF/rulii-explorer/
  index.html            import map (lit), theme bootstrap, <rx-app>
  app/
    main.js             registers the elements, starts the router, loads the descriptor
    design/             tokens.css (copied from design/, the source of truth), fonts.css, app.css, rulii-mark.svg
    components/         base.js (light-DOM LitElement bound to the store), icons.js, common.js, expression.js
    descriptor/         loader.js (states), indexes.js, format.js (labels), summaries.js (sentences)
    search/             search.js (field-weighted in-memory search)
    state/              store.js (one state object, change events, remembered theme and Plain/Raw)
    routing/            router.js (hash routes)
    graph-engine/       vendor.js (lazy d3/ELK), layout.js (ELK adapter + cache), stage.js (pan, zoom, minimap)
    features/
      shell/            app, topbar, sidebar, command palette, hover card
      overview/         landing page with the application map
      artifact/         rule, validator, compiled, rule set, rule flow (flowchart + outline), package pages
      graph/            dependency graph: model, d3 renderer, selected-artifact panel, page
      flow/             flowchart: model, rx-flowchart element, selected-step panel
      binding/          binding cross-reference
      problems/         problems list
      states/           loading, empty, not exposed, sign-in, failed, unreachable, missing
  vendor/               lit/, d3/, elk/, fonts/ (+ README.md with versions, licenses, checksums)
```

Components render into the light DOM and share one stylesheet, `app/design/app.css`, built on the
`--rx-*` tokens. Every value in it comes from `design/canvas/*.dc.html`, the approved screens.

## Tests

`mvn -pl rulii-explorer-ui test` runs `BrowserTest`: Playwright for Java starts Chromium
(downloaded on first use, about 150 MB, into the user's Playwright cache) and a JDK HTTP server
that serves these files plus the demo application's golden descriptor
(`../rulii-explorer-demo/src/test/resources/golden/order-service.json`).

- `src/test/resources/browser/tests.js` holds the in-browser unit tests (routing, indexes, search,
  summaries, rendering); the runner reports to the JUnit side.
- Every screen and state is captured in light and dark at 1440×900 into `target/screens/`, with
  console errors failing the test. When `src/test/resources/screens/<name>.png` exists it is
  compared with the capture (at most 0.5 % of pixels may differ); `-Dscreens.update=true`
  rewrites the baselines.

`spike/` (not packaged) is the M1 graph performance spike: `run-spike.ps1`, `graph-spike.html/.js`,
`RESULTS.md`.
