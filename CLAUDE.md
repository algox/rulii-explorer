# rulii-explorer — Claude Code Project Guide

## Project Overview

- **Name**: rulii-explorer — browse, search and visualise the rules, rule sets and rule flows of a running rulii application
- **Version**: 1.0.0-SNAPSHOT (Maven coordinates `org.rulii:rulii-explorer-*`)
- **Java**: 17
- **Build**: Maven, multi-module. No Node or npm anywhere: the UI is static files.
- **License**: Apache 2.0
- **Depends on**: rulii 2.1.0-SNAPSHOT, rulii-spring 2.1.0-SNAPSHOT (the 2.1 introspection API), Spring Boot 4.1.x / Spring Framework 7.0.x, Jackson 3, JUnit Jupiter 6. Versions come from the Spring Boot BOM imported in the parent POM.
- **Design docs**: `docs/REQUIREMENTS.md` and `docs/SOLUTION.md` are local-only (gitignored). The approved visual design lives in `design/` (`tokens.css` is the source of truth). `mockups/` is obsolete and never a baseline.

## Modules

| Module | Package | Contents | Depends on |
|---|---|---|---|
| `rulii-explorer-parent` | — | Parent POM: BOM import, plugin management, release profile | — |
| `rulii-explorer-core` | `org.rulii.explorer` | `descriptor/` model records + `DescriptorJson`; `builder/` `DescriptorBuilder` (walks `RuleRegistry`), `Describer`, `Expressions`, `CommandMapper`, `Sources`; `expression/` `ExpressionAnalyzer` SPI; `problem/` checks. JSON Schema at `src/main/resources/rulii-descriptor-1.schema.json`. **No Spring container**: `spring-expression` is a parser library here. | rulii, spring-expression, jackson-databind (Jackson 3) |
| `rulii-explorer-ui` | — | The single-page app under `META-INF/resources/rulii-explorer/`, vendored libraries in `vendor/` | — |
| `rulii-explorer-spring-boot-starter` | `org.rulii.explorer.boot` | Auto-configuration, `rulii` Actuator endpoint, descriptor cache, `rulii.explorer.*` properties, UI serving. **The one dependency users add.** Brings in `spring-boot-starter-actuator`; no web stack. | core, ui, rulii-spring |
| `rulii-explorer-demo` | `org.rulii.explorer.demo` | Showcase application (order-processing domain). Not published (`maven.deploy.skip`). | starter, `spring-boot-starter-webmvc` |

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
mvn -pl rulii-explorer-demo spring-boot:run   # http://localhost:8080/actuator/rulii
```

On Windows via PowerShell: `powershell.exe -Command "Set-Location 'C:\\Dev\\rules\\rulii-explorer'; mvn install 2>&1"`.

The rulii and rulii-spring snapshots must be installed locally first (`mvn install -DskipTests` in each), from their `feature/explorer-introspection` branches.

## Key Contracts

- **Descriptor**: one versioned JSON document (`descriptorVersion` = `Explorer.DESCRIPTOR_VERSION`). The JSON Schema in core (`rulii-descriptor-1.schema.json`) is the contract; SOLUTION.md §5 is the sketch it grew from. Golden file: `rulii-explorer-core/src/test/resources/golden/java-fixture.json`, validated against the schema; regenerate with `mvn -pl rulii-explorer-core -Dtest=DescriptorJsonTest -Dgolden.update=true test` and review the diff.
- **Builder rules**: artifact ids are registry names; inline members/targets get path ids (`x/members[2]`, `flow/commands[3]/target`) with `registered=false`. Rulii's own hooks (default rule set extractor/handler, the validating check) are hidden; a predefined validator is described by its `validation` section only; a supplied validation rule shows the supplied condition. Lists sort naturally (`commands[2]` before `commands[11]`).
- **Endpoint**: Actuator id `rulii` → `GET /actuator/rulii`, read-only, opt-in via `management.endpoints.web.exposure.include`.
- **UI path**: `/rulii-explorer/` (property `rulii.explorer.ui.path`).
- **Properties**: `rulii.explorer.enabled`, `rulii.explorer.ui.enabled`, `rulii.explorer.ui.path`, `rulii.explorer.include-sources`, `rulii.explorer.application-name`.
- **Sources**: the explorer reads only `RuleRegistry` and each artifact's definition. rulii-spring stamps XML-built artifacts with `SourceDefinition.forFile(location, line)`; class-based rules report their rule class; Java-built artifacts report the `@Bean` method or builder call site.

## Release

- `mvn -P release deploy` signs (GPG, `--pinentry-mode loopback`) and publishes via `central-publishing-maven-plugin`. The demo module skips deploy, javadoc, sources and signing.
