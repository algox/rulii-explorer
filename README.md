# rulii explorer

Browse, search and visualise the rules, rule sets and rule flows of a running
[rulii](https://www.rulii.org) application. The explorer reads the live rule registry, describes
every artifact in plain English, draws the dependencies and the flowcharts, and points out what
is likely to fail. It is read-only, built for developers and for the people who own the rules.

## Getting started

Add one dependency to a Spring Boot application that already uses rulii-spring:

```xml
<dependency>
    <groupId>org.rulii</groupId>
    <artifactId>rulii-explorer-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

Turn the explorer on and expose the endpoint, like any Actuator endpoint. The explorer is off by
default, so a production deployment never shows its rules unless someone chose to:

```properties
rulii.explorer.enabled=true
management.endpoints.web.exposure.include=health,rulii
```

Start the application and open `/rulii`. The same data is available as JSON at
`/actuator/rulii`. Nothing is built at startup: the descriptor is created on the first request and
cached until the context refreshes.

To see it first, run the demo in this repository and open http://localhost:8080/rulii:

```bash
mvn install
mvn -pl rulii-explorer-demo spring-boot:run
```

## What you get

- **Overview**: counts, an application map of which flows run which rule sets and rules, the
  problems, and the packages.
- **One page per artifact**: what a rule checks in plain English (and the raw expression one click
  away), its parameters and the bindings it reads and writes, who uses it, where it is defined. Rule
  sets list their rules in order with their conditions; validators show their error code, message and
  settings; compiled rules say honestly that their logic cannot be read and show the signature.
  SpEL, JavaScript and Java scripts all read as sentences; anything the explorer cannot phrase is shown
  as written.
- **Rule flows** as a flowchart (decisions, loops, scopes, an async lane, exception handlers) and as
  an outline, the text equivalent. Clicking a step shows what it runs.
- **Dependency graph** focused on one artifact and its neighbourhood, or the whole application
  grouped by package.
- **Bindings**: who writes a value, who reads it, which properties, and where compiled code might
  change it.
- **Problems**: a step that runs something that is not registered, a lookup that uses a rule's name
  instead of its registry name, duplicate names, unused rules, missing descriptions, and artifacts
  that could not be described. Each one says why it matters and how to fix it.
- **Search** across names, conditions, error codes and bindings: the search field or Ctrl/⌘ K.
- Every screen has an address you can share. Light and dark themes follow the operating system
  unless you choose. Press `?` for the keyboard, the addresses and the flowchart legend;
  [GUIDE.md](GUIDE.md) is the full guide, with a reference for every problem the checks report.

Configuration placeholders such as `${order.minTotal:100}` are shown as written, with their default.
By default the value the application resolved is never shown. An application can opt in with
`rulii.explorer.placeholders.show-values=always`: the explorer then shows, next to each placeholder,
the value the script compiled with, read from the running application rather than looked up again.
Keys matching `*password*`, `*secret*`, `*token*`, `*credential*` or `*private*` stay hidden, as
does anything the application's own Actuator `SanitizingFunction` beans would mask; add your own
patterns with `rulii.explorer.placeholders.additional-exclude`. The explorer shows what rulii knows
for certain and does not guess.

## Configuration

| Property | Default | Purpose |
|---|---|---|
| `rulii.explorer.enabled` | `false` | Master switch, off by default. The endpoint still needs Actuator exposure. |
| `rulii.explorer.ui.enabled` | `true` | Serve the UI. Turn it off to keep the JSON endpoint only. |
| `rulii.explorer.ui.path` | `/rulii` | Where the UI is served. |
| `rulii.explorer.include-sources` | `true` | Include file names, line numbers and class names in the descriptor. |
| `rulii.explorer.application-name` | `spring.application.name` | The name shown in the top bar. |
| `rulii.explorer.placeholders.show-values` | `never` | `always` shows the value each `${key:default}` placeholder compiled with, next to the key and default. |
| `rulii.explorer.placeholders.exclude` | `*password*,*secret*,*token*,*credential*,*private*` | Key globs whose values stay hidden (case-insensitive, whole key). Setting it replaces the list. |
| `rulii.explorer.placeholders.additional-exclude` | | Key globs hidden on top of `exclude`, such as `pricing.vipDiscount`. |

Context paths, a custom `management.endpoints.web.base-path` and a separate `management.server.port`
are all respected; with a separate management port the UI is served there too, next to the endpoint.
Spring MVC and WebFlux are both supported.

## Security

The descriptor is protected by whatever protects your Actuator endpoints; the UI files contain no
data. With Spring Security, a typical setup lets operators in and keeps everyone else out:

```java
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;

@Bean
SecurityFilterChain explorer(HttpSecurity http) throws Exception {
    http.securityMatcher(EndpointRequest.to("rulii"), PathPatternRequestMatcher.withDefaults().matcher("/rulii/**"))
        .authorizeHttpRequests(a -> a.anyRequest().hasRole("RULES"))
        .httpBasic(Customizer.withDefaults());
    return http.build();
}
```

If the descriptor answers 401 or 403, the UI explains what to do instead of showing an empty page.
The endpoint has no write operations.

## The descriptor in CI

The descriptor can be written at build time from any `@SpringBootTest` and compared between
releases, so a review sees exactly which rules changed:

```java
@SpringBootTest(properties = "rulii.explorer.enabled=true")
class DescriptorSnapshotTest {

    @Autowired ApplicationContext context;

    @Test
    void writeDescriptor() throws IOException {
        RuliiDescriptors.write(context, Path.of("target/rules.json"));
    }
}
```

The document follows `rulii-descriptor-1.schema.json`, shipped in `rulii-explorer-core`.

## Modules

| Module | What it is |
|---|---|
| `rulii-explorer-core` | The descriptor: model, builder, expression analysis, references and problem checks. No Spring container. |
| `rulii-explorer-ui` | The single-page app, static files with no build step. |
| `rulii-explorer-spring-boot-starter` | Auto-configuration, the Actuator endpoint and UI serving. The one dependency users add. |
| `rulii-explorer-demo` | A showcase application, with a `scale` profile that adds a thousand generated artifacts. Not published. |

## Requirements

- Java 17
- rulii and rulii-spring 2.1+
- Spring Boot 4.1+

## Building and testing

```bash
mvn install                                   # everything, with tests
mvn -pl rulii-explorer-ui test                # browser tests; the first run downloads Chromium (about 150 MB)
mvn -pl rulii-explorer-demo spring-boot:run -Dspring-boot.run.profiles=scale   # the demo with 1,000 artifacts
```

The browser tests capture every screen into `rulii-explorer-ui/target/screens` and check each with
axe-core; the scale test writes its numbers to `rulii-explorer-demo/target/scale/results.md`.

## Third-party software

The UI ships Lit (BSD-3-Clause), d3 (ISC), elkjs (EPL-2.0, unmodified) and the Newsreader, Hanken
Grotesk and JetBrains Mono fonts (OFL-1.1). The full texts are in
`rulii-explorer-ui/src/main/resources/THIRD-PARTY-NOTICES.md`.

## License


Apache License 2.0. See [LICENSE](LICENSE).
