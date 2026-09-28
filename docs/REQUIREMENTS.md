# rulii-explorer — Requirements

Status: **approved** · 2026-09-27
Supersedes [HANDOFF.md](HANDOFF.md) and the mockups in `/mockups`, which are **not** a
design baseline: the visual design starts fresh (see VQ-51).

Priority keywords: **MUST** (release blocker), **SHOULD** (expected, may slip with a reason),
**COULD** (nice to have).

---

## 1. Purpose

rulii lets developers define Rules, RuleSets and RuleFlows in Java code or as external XML
assets. In a real application these artifacts, and how they connect, *are* the business
logic, but today the only way to see them is to read source code and XML.

rulii-explorer is a web UI, running inside a rulii-spring application, that shows every rule
artifact in the running application: what it is, what it checks, what it does, and how it
connects to everything else. It is "Swagger for rules": the machine-readable descriptor it
produces is as important as the UI on top of it.

**The visual experience is a primary product goal, not a finishing touch.** The explorer is
the face of rulii for people who never read its code. It must look and feel like a polished
commercial product, and its first impression should make people want to adopt rulii. §6 turns
this into testable requirements, and a release that meets every functional requirement but
misses the §6 bar is not done.

## 2. Audience

| Persona | Wants to | Implication |
|---|---|---|
| **Developer** | Understand, debug and review rules; find what breaks if they change something | Exact names, types, bean names, source locations, raw expressions |
| **Analyst / product owner** | Understand what the business logic does without reading Java | Descriptions and plain structure first; code detail available but secondary |
| **Auditor / reviewer** | Confirm which rules exist, what they enforce, and their error codes and messages | Complete inventory; validation rules with error codes, severities and messages; nothing hidden |

The UI serves all three in one view: plain-language content first, technical detail one
level down.

## 3. Releases

| Release | Content |
|---|---|
| **R1** | Descriptor + endpoint, a browse & search UI (list, detail, search, cross-links), and **basic graph views**: dependency graph and ruleflow flowchart |
| **R2** | Richer graphs: binding data-flow layer, clustering/collapsing at scale, impact analysis ("what is affected if I change X?"), graph export |
| **R3** | Playground: run a rule, ruleset or flow against test inputs (dev/test only) |
| **R4** | Execution trace: view recorded executions (needs richer tracing in core) |

R1 is specified in detail below. R2–R4 are listed so R1 doesn't block them.

---

## 4. Functional requirements (R1)

### 4.1 Discovery

- **FR-1 (MUST)** Discover every Rule, RuleSet and RuleFlow available through the
  application's `RuleRegistry`, including beans from parent contexts.
- **FR-2 (MUST)** Cover every way an artifact can be defined:
  - `@Rule` classes (found by `@RuleScan` or the auto-configuration package scan)
  - `@Bean` methods returning a Rule, RuleSet or RuleFlow, built with the Java builders or lambdas
  - XML: `r:rule`, `r:validationRule`, the predefined validators (`r:email`, `r:notNull`, …),
    `r:ruleset` (including inline and `class-ref` members) and `r:ruleflow`
- **FR-3 (MUST)** Show each artifact's **kind**: XML·script, `@Rule` class, Java builder/lambda,
  or predefined validator.
- **FR-4 (MUST)** Show both the rule name and the Spring bean name when they differ.
- **FR-5 (SHOULD)** Show where each artifact is defined: XML resource and line, or Java class,
  method and line.

### 4.2 Artifact detail

- **FR-10 (MUST) Rule:** name, description, kind, source, pre-condition, condition (`given`),
  then-actions in order, otherwise-action, and declared parameters (name, type, optional or
  required, default value, match strategy).
- **FR-11 (MUST) Validation rule:** everything in FR-10, plus error code, severity, error
  message / default message, the value source (binding or expression), and type-specific
  settings (e.g. min/max, pattern, allowed values).
- **FR-12 (MUST) RuleSet:** name, description, source, validating flag, input parameters,
  pre-condition, initializer, stop-condition, finalizer, result extractor, error handler, and
  the **ordered** list of member rules (each linked).
- **FR-13 (MUST) RuleFlow:** name, description, source, input parameters, context, global
  exception handler, finalizer and `returning`, plus the **complete command tree** rendered
  as a readable nested outline (and as a flowchart, see FR-53). Every command type
  (bind, run, apply, execute, async-run, await / await-all / await-any, when/otherwise,
  for-each, scope, exit, custom command) with its targets, conditions, `as`/`scope`/`with`
  parameters and per-step exception handlers.
- **FR-14 (MUST) Expressions** are shown as authored: the original script text, with
  `${...}` placeholders unresolved, the script language, and syntax highlighting for `#ctx`
  bindings and placeholders.
- **FR-15 (MUST) Honest opacity:** anything whose logic is compiled Java code (lambdas,
  `@Rule` method bodies, custom commands) is labelled as opaque ("compiled code"). Its
  declared signature and parameters are still shown. The explorer never shows made-up or
  guessed content.
- **FR-17 (MUST) Plain-English expressions:** expressions are shown in plain English by
  default (e.g. `#ctx.order.total >= ${order.minTotal:100}` → "order total is at least
  *order.minTotal* (default 100)"). A toggle, per expression and globally, shows the raw
  expression (FR-14) for developers.
  - The translation is deterministic, made from the parsed expression, never guessed.
  - Any part that can't be translated is shown raw inline, and an expression that can't be
    translated at all is shown raw, with no toggle.
  - The chosen mode (plain / raw) is remembered per browser.
- **FR-16 (SHOULD)** A plain-language summary per artifact for non-developers, built from
  the description and structure (e.g. "Runs 4 rules in order; stops after 3 violations").

### 4.3 Cross-references and navigation

- **FR-20 (MUST)** Every reference is a link: ruleset → member rules, flow → run targets,
  and backlinks ("used by") on every artifact.
- **FR-21 (MUST)** Show how each flow run target is referenced (`bean-ref`, registry name,
  class, or instance) and whether it resolves now:
  - resolved: link to the target
  - late-bound (name/class lookup): resolved on a best-effort basis and labelled "dynamic lookup"
  - unresolvable: flagged as a problem
- **FR-22 (SHOULD)** Binding cross-reference as a list: which artifacts read a binding
  (from declared parameters and `#ctx.x` in scripts), and which write it (`bind`, `as`,
  `#ctx.x = …`). Writes from compiled code are marked as unknown. Showing bindings as a
  layer on the graph is R2.
- **FR-23 (SHOULD)** A "problems" list: unresolved run targets, duplicate names, rules not
  used by any ruleset or flow (informational), missing descriptions.

### 4.4 Browse and search

- **FR-30 (MUST)** A sidebar listing all artifacts, grouped by type and then by
  **package**, with counts.
  - Java artifacts use their Java package: the `@Rule` class's package, or for `@Bean`
    definitions the configuration class's package.
  - XML artifacts use their resource folder as the package (e.g. `rules/order`), with the
    file name shown on each item.
  - Anything with no known source goes under "(unknown source)".
- **FR-31 (MUST)** Search by name, description, bean name, class, error code and
  expression text, with a `/` keyboard shortcut.
- **FR-32 (SHOULD)** Filter by type, kind, package and problems.
- **FR-33 (MUST)** A landing page: an overview of counts by type and kind, sources, and problems.
- **FR-34 (MUST)** Deep links: every artifact has a stable URL that can be bookmarked or
  shared (e.g. `…/explorer/#/rule/AgeCheckRule`).

### 4.5 Graph views (basic)

R1 covers the artifact graph and the flowchart; the binding layer and scale features are R2.
The visual design of both comes from the new design work (VQ-51).

- **FR-50 (MUST) Dependency graph:** nodes for rules, rulesets and flows, encoded by shape
  *and* colour (never colour alone), with the exact encoding set by the design system.
  Edges show how certain each link is:
  - containment (ruleset → rule): solid hairline
  - runs, resolved (`bean-ref` or instance): solid arrow
  - runs, late-bound (name/class lookup): dashed arrow, labelled "dynamic lookup"
  - unresolvable target: a distinct "missing" node, also listed under problems (FR-23)
- **FR-51 (MUST) Focused by default:** the graph opens centred on the selected artifact and
  its direct neighbourhood, so it stays readable with hundreds of rules. A whole-application
  view is available, filterable by type and package.
- **FR-52 (MUST) Interaction:** pan, zoom, drag nodes, fit-to-screen. Hovering highlights a
  node's neighbourhood and fades the rest. Clicking selects the node and opens its detail.
  A legend is always visible.
- **FR-53 (MUST) RuleFlow flowchart:** the flow drawn top to bottom: sequential steps,
  `when`/`otherwise` branches, `for-each` loops, `scope` groups, `exit`, and per-step and
  global exception handlers. `async-run` steps sit in a separate async lane that joins back
  at the matching `await`. Run steps are coloured by target type and link to their target.
  Opaque steps (compiled code, custom commands) are labelled as such.
- **FR-54 (MUST) One selection everywhere:** selecting an artifact in the sidebar, the
  graph, the flowchart or a link selects it in every view. Deep links (FR-34) include the
  active view.
- **FR-55 (MUST) Accessible equivalent:** everything a graph shows is also available as
  text (detail panel, flow outline, cross-reference lists). With `prefers-reduced-motion`
  the settled layout is drawn without animation.
- **FR-56 (SHOULD) Stable layout:** reopening the same view gives the same layout, not a
  new random arrangement.

### 4.6 Descriptor (the public contract)

- **FR-40 (MUST)** One JSON document describes the whole application: all artifacts, their
  details, and the references between them. The UI reads only this document.
- **FR-41 (MUST)** It is versioned (`descriptorVersion`) and has a published JSON Schema.
  Changes within a major version are additive only.
- **FR-42 (MUST)** It can be fetched with `curl` for scripting, diffing between releases in
  CI, and generating documentation offline.
- **FR-43 (SHOULD)** Its output is stable: sorted keys and ordering, and nothing
  run-dependent (no timestamps inside artifact entries), so two builds diff cleanly.
- **FR-44 (COULD)** It can be generated at build time without starting the web layer
  (e.g. a test utility or Maven plugin), for CI diffing.

---

## 5. Non-functional requirements

### Security (production use is read-only)

- **NFR-1 (MUST)** R1 is strictly read-only. No endpoint executes, modifies or reloads rules.
- **NFR-2 (MUST)** Nothing is exposed unless the application opts in: the same exposure
  model as Spring Boot Actuator, protected by the application's existing security.
- **NFR-3 (MUST)** Never output resolved placeholder values or binding values. Showing
  `${db.password}` is fine; showing its value is not.
- **NFR-4 (SHOULD)** An option to hide source locations and class names for deployments
  where that is considered sensitive.
- **NFR-5 (MUST)** Execution features (R3) are off by default, need their own explicit
  property, and are refused when a production profile is active.

### Compatibility and packaging

- **NFR-10 (MUST)** Java 17+, Spring Boot 3.x, rulii and rulii-spring 2.x.
- **NFR-11 (MUST)** Works with Spring MVC and WebFlux, and respects the servlet context
  path and actuator base path.
- **NFR-12 (MUST)** Adding one dependency is enough (auto-configuration, zero config).
- **NFR-13 (MUST)** The UI is self-contained: no CDN or external requests at runtime, so it
  works air-gapped and under strict CSP.
- **NFR-14 (SHOULD)** The descriptor model works without Spring Boot (plain Spring and
  rulii-spring), even if the endpoint and UI need Boot.

### Performance and scale

- **NFR-20 (MUST)** Handles 1,000 artifacts: descriptor under 1 s to build, UI search
  under 100 ms.
- **NFR-21 (MUST)** Nothing is built at startup. The descriptor is built on first request
  and cached, since rule definitions are immutable after context refresh.
- **NFR-22 (MUST)** It never makes startup fail. A broken or unknown artifact is shown as
  undescribable and doesn't block the rest.

### Usability

- **NFR-30 (MUST)** Light and dark themes (system default plus an override), built on the
  rulii brand palette (VQ-5) and validated for colour-blind safety in both themes.
- **NFR-31 (MUST)** WCAG 2.1 AA: keyboard navigable, visible focus, and no identity
  conveyed by colour alone (shape + colour + label).
- **NFR-32 (MUST)** Current evergreen browsers (Chrome, Edge, Firefox, Safari).
- **NFR-33 (SHOULD)** Usable at tablet width. Phone width is best effort.

---

## 6. Visual quality and polish

The benchmark is the best current developer products (the polish level of Linear, Vercel or
Stripe's dashboards), not typical open-source admin UIs. "Wow" should come from craft and
clarity, not decoration.

### Design system

- **VQ-1 (MUST)** A single design system that every screen is built from: tokens for colour,
  a type scale, a spacing grid, radii, elevation and motion. No one-off values in components.
- **VQ-2 (MUST)** One icon set in a consistent style. The existing rulii logo is used for the
  top bar, favicon and empty states, as a vector (SVG) version so it stays crisp (VQ-4).
- **VQ-5 (MUST) rulii brand:** the explorer uses the rulii colour scheme from rulii.com (the
  deep purples, the orange accent, and the logo's orange → pink → purple gradient) and the
  site's code font, so it reads as part of the rulii family. Brand colours may be extended
  (tints, shades, a dark theme, and extra hues where artifact types need them), but never
  replaced. Where a brand colour fails WCAG AA contrast for a use, a darker or lighter shade
  of the same hue is used for that use.
- **VQ-3 (MUST)** Light and dark themes of equal quality. Dark is designed, not inverted.
- **VQ-4 (MUST)** Crisp at every pixel density: vector rendering for graphs and icons, and no
  blurry text when zoomed.

### Graph and flowchart quality

- **VQ-10 (MUST)** Labels never overlap nodes or each other, and edges don't run through
  nodes. The flowchart uses orthogonal (right-angled) edge routing with clean joins at
  branches and merges.
- **VQ-11 (MUST)** Readable at a glance: a first-time viewer can tell rules, rulesets and
  flows apart, and see which way a flow runs, without reading the legend.
- **VQ-12 (MUST)** Smooth: pan, zoom and drag at 60 fps with 500 visible nodes on a typical
  laptop.
- **VQ-13 (SHOULD)** A minimap for large graphs and flowcharts.

### Motion and interaction

- **VQ-20 (MUST)** Purposeful motion: selecting an artifact animates the camera to it,
  views cross-fade, and the graph settles into place gently. Nothing jumps, flickers or
  reflows unexpectedly. All motion respects `prefers-reduced-motion`.
- **VQ-21 (MUST)** Every interactive element has designed hover, active, focus and disabled
  states.
- **VQ-22 (MUST)** A command palette (Ctrl/⌘ K) to jump to any artifact, view or action, plus
  keyboard shortcuts shown in tooltips.
- **VQ-23 (SHOULD)** Rich hover previews: hovering a reference anywhere shows a small card
  with the target's type, description and plain-English summary.

### Every state is designed

- **VQ-30 (MUST)** Designed loading (skeletons, not spinners on a blank page), empty ("no
  flows in this application yet", with a pointer to the docs) and error states, including a
  partially describable application (NFR-22).
- **VQ-31 (MUST)** The first screen appears within 1 s on a local network, and there are no
  unstyled or half-rendered frames on load.
- **VQ-32 (MUST)** Long names, long descriptions, deep nesting and huge rulesets are handled
  gracefully (truncation with full text on hover, no broken layouts).

### Content and tone

- **VQ-40 (MUST)** Consistent, friendly and precise microcopy in plain English. Technical
  terms are introduced with a tooltip the first time they appear on a screen (e.g. "binding",
  "dynamic lookup").

### Proving it

- **VQ-50 (MUST)** A showcase demo application with a realistic domain (e.g. order
  processing) that shows off every artifact kind and view. It is what we use for
  screenshots, videos and first-time evaluation.
- **VQ-51 (MUST)** High-fidelity designs for every R1 screen and state are approved before
  that screen is built. Design starts fresh: the earlier mockups are not a baseline. It
  begins with a choice between distinct visual directions.
- **VQ-52 (MUST)** Visual regression tests: screenshots of every screen in both themes run
  in CI against the demo app, so polish doesn't regress.
- **VQ-53 (SHOULD)** A usability check with at least one non-developer before R1 is
  released: they can answer "what does this flow do?" without help.

## 7. Changes required in rulii and rulii-spring

These are prerequisites the explorer depends on. The explorer can ship a partial R1
against XML-only data before they land, but full R1 needs them.

| # | Where | Change | Needed by |
|---|---|---|---|
| C-1 | core | Fix `SourceDefinition.findElement`: it skips the old `org.algorithmx.` frames instead of `org.rulii.`, so every source location points at `SourceDefinition.build` | FR-5 |
| C-2 | core | Make flow commands describable: a `describe()` / visitor API on `RuleFlowCommand` covering `when` (condition, otherwise), `forEach` (source, item, stop), `bind`, `exit`, `scope` body and exception-handler bodies | FR-13 for Java-built flows |
| C-3 | core | Keep the script behind a script-backed Condition, Action or Function reachable, so expression text survives compilation (raw text, with placeholders unresolved) | FR-14 for Java-built script rules |
| C-4 | core | Add input params and the error handler to `RuleSetDefinition` (today they are only on the live `RuleSet`) | FR-12 |
| C-5 | spring | Record the XML resource and line on bean definitions (SourceExtractor / resource description) | FR-5 |
| C-6 | spring | Keep `param@description`, which the ruleset parser currently ignores | FR-12 |
| C-7 | core | (R4) Tracer events for nested flow commands, command-start events, timings and correlation IDs | R4 |

## 8. Out of scope for R1

- Binding data-flow graph layer, clustering at scale, impact analysis and graph export (R2)
- Execution (R3) and traces (R4)
- Editing or authoring rules through the UI
- Viewing rules that aren't in the running application (e.g. XML files on disk that aren't loaded)
- Multi-application or fleet views
- Authentication or authorisation of its own (it uses the application's security)

## 9. Open questions

1. ~~Graphs in R1?~~ **Decided 2026-09-27:** basic graph views (dependency graph and
   ruleflow flowchart) are in R1; see §4.5.
2. **Naming** *(solution phase)*: Maven artifact(s), endpoint id (`rulii`?), UI path
   (`/rulii-explorer`?), property prefix (`rulii.explorer.*`?).
3. **One module or two** *(solution phase)*: descriptor/endpoint vs UI assets.
4. ~~Plain-language expressions?~~ **Decided 2026-09-27:** plain English by default, with the
   raw expression available; see FR-17.
5. ~~Grouping at scale?~~ **Decided 2026-09-27:** group by package for now; see FR-30. Tags or
   categories in core may be revisited later.
