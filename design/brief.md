# M2 step 1 — three visual directions: shared brief

Canvas: https://claude.ai/artifact/PBLj4xrfvm7noBZ7o6KWs4 (Design type). Files go under
`<root>/project/` where `<root>` = this folder. Do NOT publish (the main session publishes).
Do NOT render/screenshot/verify (the Design type forbids it).

Format rules: the `.dc.html` rules from the Design type's SKILL.md + format.md (in context):
`<script src="./support.js"></script>` head line exactly; root element fixed 1440×900 with
matching `$preview`; all UI is `<x-dc>` markup (no innerHTML/appendChild); `{{hole}}` dotted
lookups only; classic JS `class Component extends DCLogic`; Google Fonts `css2` link in
`<helmet>` is the only network; inline stroke SVG icons, never emoji; real `<button>`/`<a>`;
text contrast ≥ 4.5:1; no gradient washes, no left-border-accent cards, no Inter/Roboto/
Arial/Fraunces.

## Artboards (1440 × 900 each)

| Direction | Overview | Flow + detail |
|---|---|---|
| A · Midnight — brand-forward | `Main.dc.html` | `A-Flow.dc.html` |
| B · Lavender — light and calm | `B-Overview.dc.html` | `B-Flow.dc.html` |
| C · Graphite — neutral professional | `C-Overview.dc.html` | `C-Flow.dc.html` |

**Theme:** every artboard supports light AND dark. Declare a `theme` tweak
(`{"editor":"enum","options":["light","dark"],"default":…}`); Overview defaults to `light`,
Flow defaults to `dark`. The top-bar theme button really works (state overrides the prop):
`const theme = this.state.theme ?? this.props.theme ?? '<default>'`. Implement tokens as CSS
custom properties set on the root via an interpolated style hole
(`style="--bg: {{t.bg}}; --ink: {{t.ink}}; …; width:1440px; height:900px"`) and use
`var(--…)` everywhere else (SVG too, via `style="fill: var(--x)"`). The logo switches with
the theme (wordmark stroke = `var(--logo-ink)`).

**Plain/raw toggle (Flow artboard):** the expression box in the detail panel has a working
"Plain / Raw" toggle (state). Plain shows the token rendering; Raw shows the monospace SpEL
with syntax colouring.

## The rulii brand (MUST use — SOLUTION.md §11.1)

- Deep purple `#301549` (nav/footer/logo ground), purple `#452C5B` (primary), orange accent
  `#F97316` (use darker orange e.g. `#C2410C` for text on light; `#F97316` is fine on dark
  purple), logo gradient orange `#F5841D` → pink `#E64E6D` → purple `#833EAE`, lavender
  surface `#FAF5FF`, code bg `#1A0E2E`, code text `#A9B7C6`, code font JetBrains Mono.
- Extend with tints/shades and a designed dark theme; never replace.
- The logo, inline SVG (no uploads available). Transparent-background version, viewBox
  `6 0 162 146`, height ~30px in the top bar:

```html
<svg viewBox="6 0 162 146" style="height: 30px; width: auto; display: block" role="img" aria-label="rulii">
  <defs>
    <linearGradient id="lg-o" gradientUnits="userSpaceOnUse" x1="14" y1="142" x2="116" y2="40"><stop offset="0.10" stop-color="#833EAE"></stop><stop offset="0.30" stop-color="#BB4683"></stop><stop offset="0.47" stop-color="#E1505F"></stop><stop offset="0.65" stop-color="#EB693F"></stop><stop offset="0.85" stop-color="#F5841D"></stop></linearGradient>
    <linearGradient id="lg-m" gradientUnits="userSpaceOnUse" x1="30.5" y1="125" x2="106.25" y2="49.25"><stop offset="0" stop-color="#F0762F"></stop><stop offset="0.22" stop-color="#E55B52"></stop><stop offset="0.37" stop-color="#DA4A6A"></stop><stop offset="0.56" stop-color="#B24589"></stop><stop offset="0.72" stop-color="#9140A3"></stop><stop offset="0.85" stop-color="#833EAE"></stop></linearGradient>
    <linearGradient id="lg-i" gradientUnits="userSpaceOnUse" x1="47.5" y1="105" x2="95.75" y2="56.75"><stop offset="0.03" stop-color="#B34588"></stop><stop offset="0.27" stop-color="#D4496F"></stop><stop offset="0.48" stop-color="#E2535C"></stop><stop offset="0.67" stop-color="#E7604C"></stop><stop offset="0.96" stop-color="#ED6F38"></stop></linearGradient>
  </defs>
  <g fill="none" stroke-width="10"><path d="M14 142V77.5A70 70 0 0 1 84 7.5" stroke="url(#lg-o)"></path><path d="M30.5 125V79.5A53.5 53.5 0 0 1 84 26" stroke="url(#lg-m)"></path><path d="M47.5 105V80.5A36.5 36.5 0 0 1 84 44" stroke="url(#lg-i)"></path></g>
  <g transform="translate(63.5 44.5) scale(0.24)"><g fill="none" style="stroke: var(--logo-ink)" stroke-width="35"><path d="M17.5 247V159.5A71 71 0 0 1 88.5 88.5H106"></path><path d="M88.5 129V157.5A71 71 0 0 0 230.5 157.5V71"></path><path d="M289.5 1V247"></path><path d="M346.5 71V247"></path><path d="M403.5 71V247"></path></g><circle cx="346.5" cy="17.5" r="17.5" fill="#F5841D"></circle><circle cx="403.5" cy="17.5" r="17.5" fill="#833EAE"></circle></g>
</svg>
```

## Artifact-type encoding (same in all directions: shape + colour + label)

| Type | Shape | Light (fill / text) | Dark (fill / text) |
|---|---|---|---|
| Rule | circle | `#833EAE` / `#6B2E94` | `#B07BE0` / `#C9A3EE` |
| Rule set | rounded square | `#F5841D` / `#B4540A` | `#FF9A3D` / `#FFB36B` |
| Rule flow | hexagon | `#0B8A86` / `#07706D` | `#37C8BF` / `#6ED9D2` |

Directions may style nodes differently (filled, outlined, tinted) but keep hue + shape.
Bindings (where shown) are neutral grey chips.

## Demo data (use exactly; no invented stats)

Application `order-service`, environment chip `staging`, rulii 2.1.0, descriptor 1.0.
**19 artifacts: 14 rules, 3 rule sets, 2 rule flows, in 4 packages.**
Kinds (rules): XML · script 9 · predefined validator 2 · @Rule class 2 · Java builder 1.
Problems: 1 error, 1 warning, 2 info.

### Packages
- `rules/order` (XML: validation.xml, approval.xml, flows.xml) — 8 rules, 2 rule sets, 1 flow
- `rules/pricing` (XML: pricing.xml) — 3 rules, 1 rule set, 1 flow
- `com.acme.order.rules` (Java) — 2 rules
- `com.acme.order.config` (Java @Bean) — 1 rule

### Rules
| Rule | Kind | Package | Condition, plain English (raw) |
|---|---|---|---|
| AgeCheckRule | XML · script | rules/order | customer age is at least 18 (`#ctx.customer.age >= 18`) |
| EmailFormatRule | validator `r:email` | rules/order | customer email is a valid email address · error `customer.email.invalid` |
| PhoneFormatRule | validator `r:pattern` | rules/order | customer phone matches the phone pattern · error `customer.phone.invalid` |
| MinTotalRule | XML · script | rules/order | order total is at least ‹order.minTotal, default 100› (`#ctx.order.total >= ${order.minTotal:100}`) |
| ShippingAddressRule | XML · script | rules/order | order shipping address is present (`#ctx.order.shippingAddress != null`) |
| CreditLimitRule | XML · script | rules/order | customer open balance plus order total is at most customer credit limit |
| ApproveOrderRule | XML · script | rules/order | fraud score is less than 0.8 → set approved to true, otherwise false |
| ManualReviewRule | XML · script | rules/order | fraud score is at least 0.5 → add order to review queue |
| VipDiscountRule | XML · script | rules/pricing | customer tier is "VIP" → set order discount to ‹pricing.vipDiscount, default 0.10› |
| FreeShippingRule | XML · script | rules/pricing | order total is at least ‹shipping.freeOver, default 75› → set shipping cost to 0 |
| RangeCheckRule | XML · script | rules/pricing | item price is more than 0 and less than ‹pricing.maxPrice, default 10000› |
| ConsistentDatesRule | @Rule class | com.acme.order.rules | compiled code · params order: Order, clock: Clock |
| StockAvailableRule | @Rule class | com.acme.order.rules | compiled code · params order: Order, inventory: InventoryService |
| FraudScoreRule | Java builder (lambda) | com.acme.order.config | compiled code · params order, customer |

### Rule sets
- **orderValidationRules** (rules/order/validation.xml:8) — validating. "Checks that an order is
  complete and valid before it is processed." Plain summary: **"Checks 8 rules in order and
  stops after 3 violations."** Pre-condition: order is present (`#ctx.order != null`).
  Stop condition: number of violations is at least 3 (`#ctx.violations.size() >= 3`).
  Members, in order: AgeCheckRule, EmailFormatRule, PhoneFormatRule, MinTotalRule,
  ShippingAddressRule, ConsistentDatesRule, StockAvailableRule, CreditLimitRule.
  Used by: orderProcessingFlow.
- **approvalRules** (rules/order/approval.xml:4) — ApproveOrderRule, ManualReviewRule.
- **pricingRules** (rules/pricing/pricing.xml:6) — VipDiscountRule, FreeShippingRule.

### Rule flows
**orderProcessingFlow** (rules/order/flows.xml:12) — "Validates, scores, prices and approves an
incoming order." Inputs: order: Order (required), customer: Customer (required).
1. bind **reviewQueue** ← bean `reviewQueue`
2. run **orderValidationRules** as `validation` (bean-ref · direct) ← the SELECTED step
3. when *validation has errors* (`#ctx.validation.hasErrors()`) → then **exit** (returns false)
4. async-run **FraudScoreRule** as `fraudCheck` (immutable context) · on FraudServiceException → bind fraudScore = 0.5
5. run **pricingRules**
6. await **fraudCheck** (timeout 5 s) — the async lane joins here
7. when *order total is at least 500* → then run **approvalRules**, otherwise execute *set approved to true* (`#ctx.approved = true`)
8. returning **approved** (`#ctx.approved`)
Global handler: on Exception → execute *set approved to false*.

**nightlyRepriceFlow** (rules/pricing/pricing.xml:30) — for each *item* in catalog items:
run 'RangeCheckRule' (by name · dynamic lookup) and run 'prefixRule' (by name · dynamic lookup).

### Problems
- **Error** · nightlyRepriceFlow runs 'prefixRule', but nothing with that name is registered.
- **Warning** · nightlyRepriceFlow looks up 'RangeCheckRule' by name, but the bean is named
  'rangeCheckRule'. Lookups use bean names.
- **Info** · ConsistentDatesRule has no description.
- **Info** · StockAvailableRule has no description.

## Screen 1 — Overview (landing page, FR-33)

- Top bar: logo · `order-service` · `staging` chip · search field "Search rules, flows, error
  codes…" with `⌘K` hint · working theme button (aria-label) · small `rulii 2.1.0`.
- Sidebar (~260px): Overview (active), Problems (badge), then RULE FLOWS (2), RULE SETS (3),
  RULES (14) grouped by package with counts; every item carries its type glyph.
- Main: a headline for the app (what it contains, one line); counts by type; the **application
  map** — a small layered picture of how it connects (2 flows → 3 rule sets → their rules, with
  the dynamic lookups dashed and 'prefixRule' as a missing node) — this is the "wow" element,
  make it beautiful; packages; problems (the 4 above); kinds breakdown. Only the data above.

## Screen 2 — Flow + detail (orderProcessingFlow)

- Same top bar and sidebar (orderProcessingFlow active under RULE FLOWS).
- Header: breadcrumb (Rule flows › orderProcessingFlow), name, description, view tabs
  Flowchart (active) | Outline | Graph, canvas tools (zoom −/+, fit, minimap toggle).
- Canvas: the flowchart of orderProcessingFlow drawn top-to-bottom in static inline SVG with
  **orthogonal (right-angled) edges**, no overlaps: start (inputs) → steps → decisions with
  yes/no branches → merge → return. The async-run sits in a separate **async lane** to the side
  and joins back at the await. The per-step exception handler hangs off the async-run; the
  global handler is noted at the bottom/side. Run steps are coloured by target type and show the
  target name; step kinds labelled (BIND, RUN, WHEN, ASYNC, AWAIT, EXIT, RETURN). The selected
  step (run orderValidationRules) is visibly selected. A minimap in a corner.
- Detail panel (~380px, right): the selected step's target, **orderValidationRules**: type
  badge (rule set), name, description, plain summary, "runs as validation", pre-condition and
  stop condition in the plain/raw expression box (working toggle), the 8 members in order (type
  glyph + kind label), used by orderProcessingFlow, source `rules/order/validation.xml:8`.

## The three directions (all on the rulii brand; they differ in layout, density, type and
how strongly the brand is applied)

- **A · Midnight — brand-forward.** Deep-purple chrome (top bar + sidebar in `#301549` family
  in BOTH themes), orange highlights for selection/focus, the logo gradient used as a sparing
  signature (e.g. the selected edge or node ring). Confident, product-launch feel. Type:
  **Sora** (display + UI) + JetBrains Mono. Medium density, generous canvas.
- **B · Lavender — light and calm.** Lavender `#FAF5FF` ground, white cards, deep-purple ink,
  orange only as small accents. Editorial and airy, friendly to analysts/auditors: bigger type,
  more whitespace, plain English front and centre. Type: **Newsreader** (serif display) +
  **Hanken Grotesk** (UI/body) + JetBrains Mono. Dark theme: calm deep purples.
- **C · Graphite — neutral professional.** Quiet warm-grey canvas, IDE-like precision,
  compact density, hairline dividers, tabular numbers. Brand kept to the logo, selection,
  primary actions and the type colours. Type: **IBM Plex Sans** + JetBrains Mono. Dark theme:
  neutral charcoal, not purple.

---

# M2 step 3 — remaining R1 screens (added 2026-09-28)

**Chosen direction: B · Lavender** (original density), light default everywhere, softer
dark. Build ON the design system: values from `C:\Dev\rules\rulii-explorer\design\tokens.css`
(both themes), specs from `C:\Dev\rules\rulii-explorer\design\DESIGN-SYSTEM.md`, and the
approved screens `B-Overview.dc.html` / `B-Flow.dc.html` plus sheets `DS-Components.dc.html`
/ `DS-Graph.dc.html` in this folder. **Copy the shell (top bar + sidebar) markup from
`B-Overview.dc.html` so every screen has the identical shell**; only the active sidebar item
changes. Every screen: 1440×900, theme tweak (default light) + working theme button, logo
inline, demo data only (below + above). No new invented facts beyond this file.

## Extra detail data

- **MinTotalRule** — bean `minTotalRule`, XML · script, `rules/order/validation.xml:31`.
  "Order total must meet the configured minimum." Given: order total is at least
  ‹order.minTotal, default 100› (`#ctx.order.total >= ${order.minTotal:100}`), language `el`.
  No then/otherwise actions. Parameters: `order: com.acme.order.Order` (required, match by
  name). Reads `order.total`. Used by: orderValidationRules (member 4 of 8).
- **EmailFormatRule** — bean `emailFormatRule`, predefined validator `r:email`,
  `rules/order/validation.xml:18`. "Customer email must be a valid address." Value source:
  binding `customer.email`. Error code `customer.email.invalid`, severity ERROR, message
  "{0} is not a valid email address." Settings: allowLocal = false, allowTopLevelDomain =
  false. Reads `customer.email`. Used by: orderValidationRules (member 2 of 8).
- **FraudScoreRule** — bean `fraudScoreRule`, Java builder (lambda), defined in
  `com.acme.order.config.RiskConfig#fraudScoreRule` (`RiskConfig.java:42`). "Scores the fraud
  risk of an order from 0 (safe) to 1." Condition and action are compiled code (honest
  opacity): condition signature `boolean test(Order order, Customer customer)`, action
  signature `void run(Order order, Customer customer)`. Parameters: `order: Order`,
  `customer: Customer`. Reads order, customer (declared); writes: unknown (compiled code).
  Used by: orderProcessingFlow (async-run as `fraudCheck`).
- **orderValidationRules** — bean `orderValidationRules`, validating = true. Input
  parameters: `order: Order` (required), `customer: Customer` (required). No initializer,
  finalizer, result extractor or error handler.
- **Binding `order`** (type `com.acme.order.Order`)
  - Read by: MinTotalRule (`order.total`), ShippingAddressRule (`order.shippingAddress`),
    CreditLimitRule (`order.total`), FreeShippingRule (`order.total`), orderValidationRules
    (pre-condition), orderProcessingFlow (when `order.total ≥ 500`), and declared parameters of
    ConsistentDatesRule, StockAvailableRule, FraudScoreRule.
  - Written by: orderProcessingFlow (input parameter), VipDiscountRule (`order.discount`),
    FreeShippingRule (`order.shippingCost`).
  - Unknown (compiled code may write): ConsistentDatesRule, StockAvailableRule, FraudScoreRule.
- **Search example:** query `total` → MinTotalRule (condition "order total is at least…"),
  CreditLimitRule, FreeShippingRule, orderProcessingFlow (step "order total is at least 500"),
  binding `order.total`.

## Screens (file → content)

| File | Screen |
|---|---|
| `S-Rule.dc.html` | Rule detail page: MinTotalRule |
| `S-Validator.dc.html` | Validation rule detail page: EmailFormatRule |
| `S-Compiled.dc.html` | Compiled rule detail page: FraudScoreRule (honest opacity) |
| `S-RuleSet.dc.html` | Rule set detail page: orderValidationRules (with membership mini-graph) |
| `S-GraphFocus.dc.html` | Dependency graph, focus mode on orderValidationRules (one hop, expandable) |
| `S-GraphAll.dc.html` | Dependency graph, whole application, grouped by package |
| `S-FlowOutline.dc.html` | orderProcessingFlow, Outline tab (text equivalent of the flowchart, FR-55) |
| `S-Binding.dc.html` | Binding cross-reference: `order` (readers, writers, unknown) |
| `S-Problems.dc.html` | Problems list (filters by severity/code, grouped) |
| `S-Search.dc.html` | Command palette open over the Overview, query `total` |
| `S-Loading.dc.html` | Loading state: skeleton of the Overview (VQ-30) |
| `S-States.dc.html` | 2×2 sheet: empty application · endpoint not exposed (shows `management.endpoints.web.exposure.include=rulii`) · sign-in required (401/403) · descriptor partially failed (1 artifact couldn't be described, rest shown) |
