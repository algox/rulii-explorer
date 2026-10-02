# Change log

## 1.0.0 (unreleased)

The first release. Requires rulii 2.1.0, rulii-spring 2.1.0 and Spring Boot 4.1 on Java 17.

- **Descriptor**: one JSON document describing every rule, rule set and rule flow of a running
  application, served read-only by the `rulii` Actuator endpoint (`GET /actuator/rulii`, opt-in
  through `management.endpoints.web.exposure.include`). Versioned (`descriptorVersion` 1.0) with a
  JSON Schema, stable ordering, and no secrets: configuration placeholders are shown as written,
  never resolved; compiled code is described by its signature only.
- **Plain English**: SpEL conditions and actions are translated into sentences, with bindings and
  placeholders as chips; the raw expression is one click away. JavaScript and Java scripts keep
  their text and a binding scan.
- **Problems**: unresolved targets, lookups that use a rule's name instead of its registry name,
  duplicate names, unused rules, missing descriptions, and artifacts that could not be described.
- **Explorer UI** at `/rulii`: overview with an application map, pages for rules,
  validators, compiled rules, rule sets, rule flows (flowchart and outline), bindings, packages
  and problems; search with a command palette (Ctrl/⌘ K); light and dark themes; every screen has
  an address.
  `?` opens a help sheet with the keyboard, the addresses and the flowchart legend; GUIDE.md is
  the full guide, with a reference for every problem code.
- **JavaScript rules**: scripts in other languages keep their text and are scanned for the bindings
  they read and write; a method call ends the path, so `ctx.order.total.doubleValue()` reads
  `order.total`. The demo has a loyalty rule set, a flow and a Java-built rule in JavaScript
  (GraalJS), in `rules/pricing/loyalty.xml` and `LoyaltyConfig`.
- **Graphs**: the dependency graph focused on one artifact or for the whole application grouped by
  package, and a flowchart per rule flow with decisions, loops, scopes, an async lane and exception
  handlers; selection is shared with the outline.
- **Spring Boot**: auto-configuration for Spring MVC and WebFlux, context and base paths, a separate
  management port (the UI follows the endpoint), `rulii.explorer.*` properties, and
  `RuliiDescriptors.write` for a build-time descriptor in CI. Off by default:
  `rulii.explorer.enabled=true` turns it on, so a production deployment never shows its rules
  unless someone chose to.
- **Quality**: golden descriptors and expression corpus, a Boot integration matrix, in-browser unit
  tests, screenshots of every screen and state, axe-core accessibility checks, and a scale test with
  a thousand generated artifacts.
