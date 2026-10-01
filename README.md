# rulii explorer

Browse, search and visualise the rules, rule sets and rule flows of a running
[rulii](https://www.rulii.org) application.

Add one dependency to a Spring Boot application:

```xml
<dependency>
    <groupId>org.rulii</groupId>
    <artifactId>rulii-explorer-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

expose the endpoint:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: rulii
```

and open `/rulii-explorer/`. The same data is available as JSON at `/actuator/rulii`.

## Modules

| Module | What it is |
|---|---|
| `rulii-explorer-core` | The descriptor: model, builder, expression analysis, references and problem checks. No Spring container. |
| `rulii-explorer-ui` | The single-page app, static files with no build step. |
| `rulii-explorer-spring-boot-starter` | Auto-configuration, the Actuator endpoint and UI serving. The one dependency users add. |
| `rulii-explorer-demo` | A showcase application. Not published. |

## Requirements

- Java 17
- rulii and rulii-spring 2.1+
- Spring Boot 4.1+

## Building

```bash
mvn install
mvn -pl rulii-explorer-demo spring-boot:run
```

## License

Apache License 2.0. See [LICENSE](LICENSE).
