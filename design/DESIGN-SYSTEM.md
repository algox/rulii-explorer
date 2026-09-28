# rulii explorer design system (direction B · Lavender)

Status: **approved** · 2026-09-28

The tokens in [`tokens.css`](tokens.css) are the single source of truth. Every component
below uses them and nothing else (VQ-1). The visual reference sheets are on the design
canvas (https://claude.ai/artifact/PBLj4xrfvm7noBZ7o6KWs4), in the "Design system" row.

## 1. Principles

1. **Plain English first, code one click away.** Descriptions, summaries and translated
   expressions lead; exact names, source locations and raw expressions sit one level down.
2. **Calm surfaces, precise details.** A lavender ground, white cards, hairline borders and
   almost no shadows. Emphasis comes from type and spacing, not from boxes and colour.
3. **Brand in small doses.** Deep purple ink everywhere; orange only for selection, the
   active tab and the environment chip. The logo gradient appears only in the logo.
4. **Identity is never colour alone.** Every artifact type has a shape, a colour and a text
   label (NFR-31).
5. **Honest about fidelity.** Dynamic lookups are dashed, missing targets are marked,
   compiled code says so (FR-15, FR-21).

## 2. Foundations

### Colour

| Role | Tokens | Notes |
|---|---|---|
| Surfaces | `--rx-bg`, `--rx-bar`, `--rx-sunken`, `--rx-card`, `--rx-hover`, `--rx-selected` | Page → bars → cards. Sunken is for wells and segmented controls. |
| Ink | `--rx-ink`, `--rx-ink-2`, `--rx-ink-3` | Primary, secondary, tertiary. All three pass 4.5:1 on every surface. |
| Lines | `--rx-line`, `--rx-line-strong`, `--rx-edge` | Hairlines, control borders, graph edges. |
| Accent | `--rx-accent`, `--rx-accent-soft` | Orange. Selection, active tab, links. Text-safe shade in light. |
| Focus | `--rx-focus` | Purple keyboard-focus outline, deliberately different from selection. |
| Primary action | `--rx-primary`, `--rx-primary-hover`, `--rx-on-primary` | One primary button per view at most. |
| Types | `--rx-rule*`, `--rx-ruleset*`, `--rx-flow*`, `--rx-binding*` | Base, `-text`, `-soft` (fill), `-line` (border) for each. |
| Status | `--rx-error*`, `--rx-warning*`, `--rx-info*`, `--rx-success*` | Always with an icon and a word, never colour alone. |
| Graph | `--rx-canvas`, `--rx-canvas-dot`, `--rx-lane`, `--rx-lane-line` | Canvas ground, dot grid, async lane. |
| Code | `--rx-code-*` | rulii.com's code-block colours, identical in both themes. |

The **dark theme** is the soft, lifted purple approved on 2026-09-27 (`#221433` page,
`#291A3D` bars, `#2F2045` cards). Light is the default everywhere.

### Type

| Style | Font | Size / line height | Weight | Use |
|---|---|---|---|---|
| Display | Newsreader | 40 / 1.15, −0.01em | 500 | Overview headline |
| Page title | Newsreader | 38 / 1.15 | 500 | Artifact name on full detail pages |
| Title | Newsreader | 26 / 1.2 | 500 | Artifact name in panels and flow headers |
| Heading | Newsreader | 19 / 1.3 | 500 | Section and panel headings |
| Subheading | Newsreader | 15 / 1.35 | 600 | Card titles, plain-English summaries |
| Body large | Hanken Grotesk | 14 / 1.5 | 400 | Descriptions |
| Body | Hanken Grotesk | 13 / 1.45 | 400–600 | Default UI text |
| Small | Hanken Grotesk | 12 / 1.45 | 400–500 | Secondary UI text, table cells |
| Caption | Hanken Grotesk | 11 / 1.35 | 500 | Metadata, kind labels |
| Overline | Hanken Grotesk | 11, 0.08em, uppercase | 600 | Section labels, step kinds |
| Code | JetBrains Mono | 12 / 1.55 (11 small) | 400 | Names, bindings, paths, raw expressions |

Numbers use `font-variant-numeric: tabular-nums` wherever they line up.

### Space, shape, elevation, motion

- **Space:** a 4-px rhythm with 2-px half steps (`--rx-space-*`: 2, 4, 6, 8, 10, 12, 16,
  20, 24, 32, 40).
- **Layout:** top bar 64 px, sidebar 264 px, detail panel 384 px, page padding 32 × 40,
  card padding 16 × 20.
- **Radii:** 4 chips · 6 controls · 8 buttons, inputs, nodes · 12 cards · 16 hero cards and
  dialogs · pill for status badges.
- **Elevation:** flat with hairlines by default. Shadow 1 for raised controls, 2 for
  popovers and hover cards, 3 for dialogs and the command palette.
- **Motion:** 120 ms for hover and press, 200 ms for panels and toggles, 360 ms for camera
  moves and graph settling, with `--rx-ease`. All zero under `prefers-reduced-motion`.

## 3. Components

Every interactive component has designed **rest, hover, pressed, focus-visible, selected
and disabled** states (VQ-21). Focus is a 2-px purple ring offset by 2 px
(`--rx-focus-ring`); selection is a 2-px orange ring (`--rx-selection-ring`).

| Component | Anatomy and variants |
|---|---|
| **Button** | 34 px, radius 8, 13/600. *Primary* (filled `--rx-primary`), *secondary* (card fill, strong border), *ghost* (no border), *icon* (34 × 34, aria-label). Small size 28 px. |
| **Segmented control** | Sunken track, radius 8; items 28 px, radius 6; selected item is a card with shadow 1. Used for Plain / Raw and view switches. |
| **Tabs** | 40 px, 13/600, ink-3 at rest; the active tab gets ink and a 2-px orange underline. |
| **Search field** | 38 px, radius 8, card fill, strong border, search icon, `⌘K` kbd hint on the right. |
| **Command palette** | Dialog 640 px wide, shadow 3, radius 16: search field, results grouped by type with type glyphs, a footer of keyboard hints. |
| **Type glyph** | 14 px shape in the type colour: circle (rule), rounded square (rule set), hexagon (flow). Always next to a text label. |
| **Type badge** | Pill: glyph + "Rule" / "Rule set" / "Rule flow", `-soft` fill, `-text` ink, `-line` border. |
| **Kind label** | Caption: "XML · script", "Validator · r:email", "@Rule class", "Java builder". Compiled kinds get a small "compiled" lock icon. |
| **Binding chip** | Mono 12, radius 4, `--rx-binding` fill, e.g. `order.total`. Reads show `←`, writes show `→`. |
| **Placeholder chip** | Mono 12 key plus a "default 100" caption, dashed `--rx-rule-line` border: marks a `${…}` placeholder in plain English. |
| **Status badge** | Pill with icon + word: Error, Warning, Info, Success. |
| **Environment chip** | Pill, `--rx-accent-soft` fill, accent ink: `staging`. |
| **Card** | Card fill, 1-px `--rx-line`, radius 12, padding 16 × 20; header = subheading + optional action. |
| **Stat card** | Card with an overline label, a Newsreader 40 number (tabular) and a one-line caption. |
| **Sidebar item** | 32 px row, radius 8, glyph + name + count; hover fill, selected fill with ink. Group headers are overlines with counts. |
| **List row** | 32–40 px, hairline separators; name (mono or UI), kind caption, trailing chevron. |
| **Problem row** | Status icon + message (body) + artifact link (mono) + code caption (e.g. UNRESOLVED_TARGET). |
| **Expression box** | Card with a segmented Plain / Raw control. *Plain*: tokens rendered inline, bindings and placeholders as chips, untranslatable parts in mono. *Raw*: `--rx-code-bg` block with the syntax colours. |
| **Detail panel** | 384 px, sunken fill, left hairline: type badge, title, description, plain summary, sections with overline headings, "used by" links, source line (mono) with a copy button. |
| **Hover card** | 320 px, shadow 2, radius 12: type badge, name, plain summary, "Open" link (VQ-23). |
| **Tooltip** | Ink fill (inverse text), radius 6, 12/500, 6 × 8 padding, shows keyboard shortcuts. |
| **Empty, loading, error states** | Centered illustration using the logo mark in line style, a heading and one action; skeletons use `--rx-sunken` blocks with a slow shimmer (VQ-30). |

## 4. Graph language

### Dependency graph nodes

| Node | Shape | Rest | Hover | Selected |
|---|---|---|---|---|
| Rule | Circle dot with a label (no box) | `--rx-rule` dot, name in mono 12, kind caption | `--rx-rule-soft` halo behind dot and label | 2-px orange ring |
| Rule set | Rounded rectangle with a square glyph | Card fill, `--rx-ruleset-line` border, name + "8 rules" caption | `--rx-ruleset-soft` fill | 2-px orange ring |
| Rule flow | Pill with a hexagon glyph | Card fill, `--rx-flow-line` border, name + "8 steps" caption | `--rx-flow-soft` fill | 2-px orange ring |
| Missing target | Pill | No fill, dashed `--rx-error` border, name in `--rx-error`, "not registered" caption | — | 2-px orange ring |

Hovering a node fades everything outside its neighbourhood to 16% opacity.

### Edges (fidelity tiers, FR-50)

| Edge | Style |
|---|---|
| Contains (rule set → rule) | 1-px solid `--rx-edge`, no arrowhead |
| Runs, resolved | 1.5-px solid `--rx-edge`, small filled arrowhead |
| Runs, dynamic lookup | 1.5-px dashed (4 3) `--rx-edge`, arrowhead, "dynamic lookup" label on hover |
| Runs, unresolved | 1.5-px dashed `--rx-error`, arrowhead into the missing node |
| Highlighted (hover/selection path) | Same style in `--rx-ink`, 2 px |

All edges are orthogonal (right-angled) with 8-px rounded corners, routed by ELK.

### Flowchart steps

- **Step node:** 44 px high, radius 8, card fill, 1-px line; an overline kind label (BIND,
  RUN, APPLY, EXECUTE, ASYNC, AWAIT, EXIT, RETURN) above the step text.
- **Run steps** use the target type's `-soft` fill and `-line` border, with its type glyph.
  A run step whose target is a **dynamic lookup** (by name or class) has a dashed border in
  the type colour; an unresolved target has a dashed `--rx-error` border.
- **Decision (when / for-each):** a hexagon-ended node (pointed left and right) with the
  plain-English condition; outgoing branches labelled "yes" / "no" (when) or "each item"
  (for-each) in caption style.
- **Containers (scope, for-each body):** a rounded group with a hairline border and an
  overline title.
- **Async lane:** a `--rx-lane` band with a solid `--rx-lane-line` border, to the right of
  the main spine, joining back at its `await` with an arrow.
- **Exception handler:** a small tag hanging off its step, dashed `--rx-warning` border,
  "on FraudServiceException" in caption, with its body inside.
- **Start and return:** pill terminals; start lists the inputs, return shows the result
  expression.
- **Opaque steps:** mono "compiled code" caption with a lock icon.
