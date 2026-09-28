# rulii explorer design

The approved design for rulii explorer R1 (milestone M2, completed 2026-09-28): direction
**B · Lavender**, its design system, and hi-fi designs of every R1 screen and state.

- **Live canvas** (view, compare, toggle themes): https://claude.ai/artifact/PBLj4xrfvm7noBZ7o6KWs4
- **Requirements** it answers: [REQUIREMENTS.md](../docs/REQUIREMENTS.md) §6 (visual quality)
- **Solution context**: [SOLUTION.md](../docs/SOLUTION.md) §11 (design process, brand palette)

## Contents

| Path | What it is |
|---|---|
| [`tokens.css`](tokens.css) | **The single source of truth**: colours (light and dark), type, space, layout, shape, elevation, motion. The real UI loads this file. |
| [`DESIGN-SYSTEM.md`](DESIGN-SYSTEM.md) | Principles, foundations, component specs with their states, and the graph language. |
| [`brand/`](brand/README.md) | The rulii logo as SVG: full, on-dark, on-light, and the square mark. |
| [`brief.md`](brief.md) | The brief the designs were built from, including the demo data (the order-processing application) every screen uses. |
| [`canvas/`](canvas/) | The source of every artboard on the canvas, plus `canvas.json` (its layout). |

## The canvas files

These are Design Component files (`.dc.html`). They render on the design canvas, which
provides their runtime (`support.js`); opened directly in a browser they don't render.
`canvas.json` holds the layout, so the whole canvas can be republished from this folder.

### Approved: direction B · Lavender

| File | Screen |
|---|---|
| `B-Overview.dc.html` | Overview (landing page) with the application map |
| `B-Flow.dc.html` | orderProcessingFlow: flowchart + detail panel |
| `S-Rule.dc.html` | Rule detail: MinTotalRule |
| `S-Validator.dc.html` | Validation rule detail: EmailFormatRule |
| `S-Compiled.dc.html` | Compiled rule detail: FraudScoreRule (honest opacity) |
| `S-RuleSet.dc.html` | Rule set detail: orderValidationRules |
| `S-GraphFocus.dc.html` | Dependency graph, focus mode |
| `S-GraphAll.dc.html` | Dependency graph, whole application |
| `S-FlowOutline.dc.html` | orderProcessingFlow, outline (text equivalent of the flowchart) |
| `S-Binding.dc.html` | Binding cross-reference: `order` |
| `S-Problems.dc.html` | Problems list |
| `S-Search.dc.html` | Command palette over the overview |
| `S-Loading.dc.html` | Loading state |
| `S-States.dc.html` | Empty, not exposed, sign-in required, partially described |

### Design system sheets

| File | Sheet |
|---|---|
| `DS-Foundations.dc.html` | Palettes (both themes) with contrast ratios, type, space, radii, elevation, motion |
| `DS-Components.dc.html` | Every component in its states |
| `DS-Graph.dc.html` | Nodes, edges, flowchart steps, composed examples, legend |

### Explorations (not chosen, kept for the record)

| File | Direction |
|---|---|
| `Main.dc.html`, `A-Flow.dc.html` | A · Midnight (brand-forward) |
| `C-Overview.dc.html`, `C-Flow.dc.html` | C · Graphite (neutral professional) |
| `Lavender-Overview.dc.html`, `Lavender-Flow.dc.html` | B's look at C's density |

## Decisions made during M2

- Direction B · Lavender, at its original (airier) density.
- The same light colour scheme on every page by default; a softer dark theme (lifted
  purples, never near-black) is available.
- Main rulii palette from rulii.com, not the brighter violet of its Spring pages.
- A 38 px serif "Page title" style for full detail pages.

## Open points from the review

- The binding page has no sidebar entry yet (there is no Bindings section).
- Outline expand/collapse and graph "expand" are drawn but not interactive.
- Tablet width (NFR-33, a SHOULD) is not designed yet.
