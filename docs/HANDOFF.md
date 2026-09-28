# rulii-explorer — Design Handoff

Continuation of a design conversation started in the rulii-spring workspace (2026-08-25).
This document captures everything decided or discovered so far, so work can continue here
with no context from that chat.

## What this is

A runtime UI for the rulii rule engine — "Swagger for rules." It introspects all
Rules / RuleSets / RuleFlows in a Spring context (via `RuleRegistry`) and gives users a
visual view of them: their metadata, expressions, and how they are all connected.
Audience includes non-developers (analysts, product owners, auditors) — rule
interconnections are the business logic, and this makes them visible.

**Interactive mockup (approved by Max — "love it"):**
- Live artifact: <https://claude.ai/code/artifact/67cbaba3-48b5-452a-93dd-f90c170ce7c0>
- Source: [`mockups/rulii-explorer-mock.html`](../mockups/rulii-explorer-mock.html) — fully
  self-contained HTML/CSS/JS, no external dependencies (built under artifact CSP). Open
  directly in a browser.

## Architecture recommendation

- **Separate module** (this workspace), not inside rulii-spring — same relationship
  springdoc has to Spring. Suggested split later: `rulii-explorer-core` (descriptor
  model + endpoint) and `rulii-explorer-ui` (static assets), or a single module to start.
- **Descriptor endpoint as a Spring Boot Actuator endpoint** (`@Endpoint(id = "rulii")`)
  rather than a plain `@RestController` — inherits actuator's exposure model, security
  integration, and works for both MVC and WebFlux.
- **The descriptor JSON document is the real product** (the OpenAPI-spec analog); the UI
  is just a client of it. Design it as a versioned public contract
  (`descriptorVersion`). Independently useful for CI diffing ("what rules changed in
  this release?") and offline doc generation. A draft shape is embedded in the mockup's
  "Descriptor" tab.
- **Static single-page UI** served off that JSON, `@ConditionalOnWebApplication`, gated
  by `rulii.explorer.enabled` (property names TBD).
- **Real product should use actual d3-force** (the mock hand-rolls a ~60-line
  simulation) — gives the same physics plus zoom/pan and better collision handling.

## Core introspection audit (rulii core, C:\Dev\rules\rulii)

What already exists (~70% of what's needed):

- **Rules**: `org.rulii.rule.RuleDefinition` carries name, description, ruleClass,
  `inline` flag, `SourceDefinition`, and `MethodDefinition`s for
  pre-condition/given/then/otherwise. `MethodDefinition` includes `ParameterDefinition`s
  → each rule's **declared binding reads come for free**.
- **RuleSets**: `RuleSet.getDefinition()` → `RuleSetDefinition` containing child
  `RuleDefinition`s. Containment edges are trivial. Also `getRules()`,
  pre/stop-conditions in the definition.
- **RuleFlows**: `RuleFlow.getCommands()` is already on the interface
  (`org.rulii.ruleflow.RuleFlow`, ~line 77) — the command tree is reachable.

Gaps that need core changes (Max owns core, so these are contained):

1. **Command internals are not uniformly readable**: `ContainerCommand.getBody()` is
   protected; `WhenCommand`'s otherwise-body is private (setter only); run
   targets/conditions not exposed. → Add a `describe()` method or visitor on
   `RuleFlowCommand` emitting a serializable node (`type`, `label`, `target`,
   `resolution`, `children`).
2. **Script/SpEL expression text retention**: `SourceDefinition` does not appear to
   retain original expression source, so an XML rule's `#ctx.age >= 18` may not survive
   into its definition. Needs verification; likely a small core change to carry source
   text through. This matters most — XML/SpEL rules are the most viewer-relevant kind.

## Graph semantics — fidelity tiers (a core design principle)

Three edge types with different confidence levels; the UI renders them distinctly and
never pretends to more fidelity than it has:

| Edge | Source of truth | Rendering |
|---|---|---|
| Containment (ruleset → rules, flow → commands) | definitions — exact | solid, hairline |
| Flow run targets, `bean-ref` | resolvable at render time | solid arrow, "runs" |
| Flow run targets, `name`/`class` registry lookup | late-bound; resolve best-effort | **dashed arrow, "dynamic lookup"** |
| Data flow: reads | `ParameterDefinition`s — exact | dotted gray, binding → rule |
| Data flow: writes | SpEL AST walk for `#ctx.x = ...`; **opaque for lambdas** | dotted gray, rule → binding |

Lambda/class rules with compiled bodies are shown honestly as opaque ("condition and
action bodies are compiled code; declared parameters still introspected") — see
`FraudScoreRule` in the mock. A misleading graph is worse than a partial one.

## UI design spec (as implemented in the mock)

**Layout**: three-pane inspector — top bar (brand, env chip, counts, search with `/`
shortcut) · left sidebar (grouped, searchable artifact list) · center canvas with tabs ·
right detail panel (316px).

**Tabs**: Dependency graph · RuleFlow (flowchart) · Descriptor (JSON) · Playground
(deliberately disabled, labeled "phase 4" — roadmap made visible).

**Node encoding** (shape + color, so identity is never color-alone):
- Rule = circle, blue
- RuleSet = rounded square, orange
- RuleFlow = diamond, aqua
- Binding = small gray dot (neutral + shape-coded — avoids a 4th categorical hue)

**Palette** — validated colorblind-safe categorical slots (first three slots pass
all-pairs CVD testing in both modes), token-based theming with
`prefers-color-scheme` + `data-theme` override:

| Role | Light | Dark |
|---|---|---|
| Rule (slot 1, blue) | `#2a78d6` | `#3987e5` |
| RuleSet (slot 2, orange) | `#eb6834` | `#d95926` |
| RuleFlow (slot 3, aqua) | `#1baf7a` | `#199e70` |
| Binding / muted | `#898781` | `#898781` |
| Page / surface | `#f9f9f7` / `#fcfcfb` | `#0d0d0d` / `#1a1a19` |
| Ink primary / secondary | `#0b0b0b` / `#52514e` | `#ffffff` / `#c3c2b7` |
| Hairline | `#e1e0d9` | `#2c2c2a` |

**Type**: system sans (`system-ui`) for UI; `ui-monospace` (Cascadia/Consolas) for
expressions, bean names, code — terminal vernacular fits a dev tool. Uppercase
letter-spaced labels for section headers.

**Interactions**: drag nodes; hover traces neighborhood (rest fades to 0.16 opacity);
click opens detail panel; "show bindings" toggle (data-flow layer can get hairy at
scale); tooltip on hover; flowchart run-targets click through to the graph;
`prefers-reduced-motion` renders the settled layout statically.

**Detail panel**: type badge, name, description, kind (XML·SpEL / @Rule class /
inline·lambda / predefined validator), source location, message code, expressions with
`#ctx` and `${...}` placeholder syntax highlighting, params, ordered member list
(rulesets), run targets with resolution labels (flows), used-by backlinks (all
clickable navigation), binding chips (`x ←` reads, `x →` writes).

**Flow view**: main spine (param → bind → run → when/otherwise branch → await →
returning) with a separate **async lane** (async-run + on-exception handler + result
merge), left accent bars colored by target type, small-caps step-kind labels.

## Sample data domain (for demos)

Order-processing domain modeled on Max's own docs/tests so it reads real:
`AgeCheckRule`, `MinTotalRule` (shows `${order.minTotal:100}` env placeholder),
`EmailFormatRule` (predefined `r:email`), `ConsistentDateRule` (@Rule class),
`ApproveRule` (terse given/then/otherwise), `ThresholdRule`, `FraudScoreRule` (lambda —
the opaque case), `RangeCheckRule` + `PrefixRule` (dynamic registry lookups);
rulesets `orderValidationRules`, `approvalRules`; flows `orderProcessingFlow` (async +
await + on-exception), `nightlyRepriceFlow` (for-each + dynamic lookups);
bindings `order`, `customer`, `approved`, `fraudScore`, `openOrders`.

## Phasing plan

1. **Descriptor model + actuator endpoint** — DTOs, the core `describe()`/source-text
   additions, JSON out. Useful via `curl` on day one, no UI needed.
2. **UI: list/detail/search** — browse rules with metadata, expressions, params,
   message codes.
3. **Graph views** — ruleflow flowcharts, ruleset membership, binding cross-reference
   ("what reads `order.total`?").
4. **Playground + trace** — opt-in execution with binding inputs (read-only viewer by
   default; playground behind its own explicit property, dev/test-only — SpEL runs with
   full `StandardEvaluationContext` power and actions mutate state; execute against
   scratch `Bindings`). Later: wire in rulii tracing for historical executions
   (observability direction).

## Open questions (not yet decided)

- Settle the **descriptor JSON schema first** — it's the public contract; everything
  else (endpoint, UI, graph) is swappable behind it.
- Landing view: graph vs. list? (Mock lands on graph with `orderProcessingFlow`
  pre-selected.)
- Density strategy at real-world scale (100s of rules): clustering, filtering by
  package/ruleset, or ruleset-collapsed default?
- Module/artifact naming, property prefix (`rulii.explorer.*`?), Java/Maven scaffolding
  for this workspace (nothing initialized yet — no git repo, no pom).

## Related versions (as of 2026-08-25)

- rulii core & rulii-spring: 1.3.0-SNAPSHOT, Java 17, Spring 6.2.x / Boot 3.x.
- rulii-spring `<r:ruleflow>` XML namespace and script `${...}` placeholder support are
  complete (see rulii-spring CLAUDE.md for the full feature reference).
