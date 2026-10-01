# rulii-explorer — Claude Code Project Guide

## Project Overview

- **Name**: rulii-explorer — browse, search and visualise the rules, rule sets and rule flows of a running rulii application
- **Version**: 1.0.0-SNAPSHOT (Maven coordinates `org.rulii:rulii-explorer-*`)
- **Java**: 17
- **Build**: Maven, multi-module. No Node or npm anywhere: the UI is static files.
- **License**: Apache 2.0
- **Depends on**: rulii 2.1.0-SNAPSHOT, rulii-spring 2.1.0-SNAPSHOT (the 2.1 introspection API), Spring Boot 4.1.x / Spring Framework 7.0.x, Jackson 3, JUnit Jupiter 6. Versions come from the Spring Boot BOM imported in the parent POM.
- **Design docs**: `docs/REQUIREMENTS.md`, `docs/SOLUTION.md`, `design/` (the approved visual design; `design/tokens.css` is the source of truth the UI module copies in M3) and `mockups/` (obsolete, never a baseline) are all local-only: gitignored and untracked, kept on Max's machine.

## Modules

| Module | Package | Contents | Depends on |
|---|---|---|---|
| `rulii-explorer-parent` | — | Parent POM: BOM import, plugin management, release profile | — |
| `rulii-explorer-core` | `org.rulii.explorer` | `descriptor/` model records + `DescriptorJson`; `builder/` `DescriptorBuilder` (walks `RuleRegistry`), `Describer`, `Expressions`, `CommandMapper`, `Sources`; `expression/` `ExpressionAnalyzer` SPI with `spel/SpelExpressionAnalyzer` (language `el`; the default analyzer set); `problem/` checks. JSON Schema at `src/main/resources/rulii-descriptor-1.schema.json`. **No Spring container**: `spring-expression` is a parser library here. | rulii, spring-expression, jackson-databind (Jackson 3) |
| `rulii-explorer-ui` | — | The single-page app under `META-INF/resources/rulii-explorer/`, vendored libraries in `vendor/` | — |
| `rulii-explorer-spring-boot-starter` | `org.rulii.explorer.boot` | `RuliiExplorerAutoConfiguration` (after rulii-spring's `RuleConfig`, needs a `RuleRegistry` bean, `rulii.explorer.enabled`), `RuliiExplorerProperties`, `DescriptorService` (lazy build, cache + ETag, cleared on `ContextRefreshedEvent`, failure = error payload), `RuliiDescriptorEndpoint` (`@Endpoint(id="rulii")`, returns a Jackson 3 `JsonNode` with nulls omitted; 500 on build failure), `RuliiDescriptors.write(context, path)` for CI. UI serving arrives in M3. **The one dependency users add.** Brings in `spring-boot-starter-actuator`; no web stack. | core, ui, rulii-spring |
| `rulii-explorer-demo` | `com.acme.order` | The showcase `order-service` from the design brief: `OrderServiceApplication` with `@RuleScan(scanBasePackages = "com.acme.order.rules", xmlLocations = rules/order, rules/pricing)`; XML rules in `src/main/resources/rules/{order,pricing}/*.xml`, two `@Rule` classes (no descriptions, on purpose), `RiskConfig.fraudScoreRule` (lambdas), `PricingConfig.rangeCheckRule` (SpEL script, bean name differs from rule name on purpose), `nightlyRepriceFlow` runs a missing `prefixRule` on purpose. Golden: `src/test/resources/golden/order-service.json` (sources on; regenerate with `-Dtest=OrderServiceDescriptorTest -Dgolden.update=true`). Not published (`maven.deploy.skip`). | starter, `spring-boot-starter-webmvc` |

Note Spring Boot 4 names: the MVC starter is `spring-boot-starter-webmvc` (not `-web`), and Jackson is `tools.jackson.core:jackson-databind`.

## Coding Style

- **License header**: every source file (main and test) starts with the Apache 2.0 block. Copy from any existing file.
- **Explicit `super()` in every constructor**, as in rulii.
- Javadoc on public types and methods; `@author Max Arulananthan`, `@since 1.0`.
- Descriptor output must be stable: fixed key order, arrays sorted by type then ID, no timestamps in the body.

## Build & Test

```bash
mvn install                 # whole reactor, runs tests
mvn -pl rulii-explorer-core test
mvn -pl rulii-explorer-demo spring-boot:run   # http://localhost:8080/actuator/rulii  (the demo exposes health,rulii)
```

On Windows via PowerShell: `powershell.exe -Command "Set-Location 'C:\\Dev\\rules\\rulii-explorer'; mvn install 2>&1"`.

The rulii and rulii-spring snapshots must be installed locally first (`mvn install -DskipTests` in each), from their `feature/explorer-introspection` branches.

## Key Contracts

- **Descriptor**: one versioned JSON document (`descriptorVersion` = `Explorer.DESCRIPTOR_VERSION`). The JSON Schema in core (`rulii-descriptor-1.schema.json`) is the contract; SOLUTION.md §5 is the sketch it grew from. Golden file: `rulii-explorer-core/src/test/resources/golden/java-fixture.json`, validated against the schema; regenerate with `mvn -pl rulii-explorer-core -Dtest=DescriptorJsonTest -Dgolden.update=true test` and review the diff.
- **Builder rules**: artifact ids are registry names; inline members/targets get path ids (`x/members[2]`, `flow/commands[3]/target`) with `registered=false`. Rulii's own hooks (default rule set extractor/handler, the validating check) are hidden; a predefined validator is described by its `validation` section only; a supplied validation rule shows the supplied condition. Lists sort naturally (`commands[2]` before `commands[11]`).
- **SpEL analysis**: `Placeholders` pre-pass (`${k:d}` becomes `#__phN`, never resolved), `SpelTranslator` AST walk through the `Phrases` phrase book, raw tokens for anything else (`T()`, `@bean`, projections, unknown `#vars`). Golden corpus: `rulii-explorer-core/src/test/resources/golden/spel-corpus.md` (Markdown table, regenerate with `-Dtest=SpelExpressionAnalyzerTest -Dgolden.update=true`). Add phrases to `Phrases`, add every new case to the corpus.
- **Endpoint**: Actuator id `rulii` → `GET /actuator/rulii`, read-only, opt-in via `management.endpoints.web.exposure.include`. `WebEndpointResponse` carries no headers, so ETag/Last-Modified are not sent by the Actuator endpoint; `DescriptorService.Snapshot` has `etag()`/`builtAt()` for the UI handlers (M3). Starter tests: `src/test/resources/application.properties` excludes Spring Security auto-config (the security test clears the exclusion) and pins `spring.main.web-application-type=servlet` (the WebFlux test sets `reactive`); tests use the JDK `HttpClient`, since Boot 4 dropped `TestRestTemplate` from `spring-boot-starter-test`.
- **UI path**: `/rulii-explorer/` (property `rulii.explorer.ui.path`).
- **Properties**: `rulii.explorer.enabled`, `rulii.explorer.ui.enabled`, `rulii.explorer.ui.path`, `rulii.explorer.include-sources`, `rulii.explorer.application-name`.
- **Sources**: the explorer reads only `RuleRegistry` and each artifact's definition. rulii-spring stamps XML-built artifacts with `SourceDefinition.forFile(location, line)`; class-based rules report their rule class; Java-built artifacts report the `@Bean` method or builder call site.

## Release

- `mvn -P release deploy` signs (GPG, `--pinentry-mode loopback`) and publishes via `central-publishing-maven-plugin`. The demo module skips deploy, javadoc, sources and signing.
