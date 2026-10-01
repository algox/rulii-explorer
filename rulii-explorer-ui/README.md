# rulii-explorer-ui

The explorer's single-page app, shipped as static files under
`src/main/resources/META-INF/resources/rulii-explorer/`. There is no build step: the files are
served as written, with Lit, d3 and ELK copied into `vendor/` as pinned ES modules
(SOLUTION.md §9 and §14).

The app is built in milestone M3 against the approved design in `../design/`
(`tokens.css` is the single source of truth). Until then this module holds a placeholder page.

Planned layout:

```
rulii-explorer/
  index.html        import map, app shell
  app/
    design/         tokens.css, themes, typography, motion presets
    components/     design-system primitives
    descriptor/     JSDoc typedefs for the descriptor; loader; indexes
    state/          store: descriptor, selection, view, preferences
    features/       shell, overview, artifact, graph, flow, problems
    graph-engine/   layout interface, ELK adapter + worker, d3 SVG renderer, minimap
    routing/        hash routes
  vendor/           lit/, d3/, elk/ (+ README.md with versions and licenses)
```
