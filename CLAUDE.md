# XENO Engineering Guidelines — Multitenant Datasource Spring Boot Starter
**Library Development Standards — v1.0**
*(derived from the XENO Platform-Wide Standards v1.2, as restated for Vida Wealth, and narrowed to a shared library)*

This document defines the engineering standards for **`com.xenotech:multitenant-datasource-spring-boot-starter`**, the Spring Boot starter that gives XENO services one database per tenant (country). When generating, reviewing, or reasoning about code in this repository, apply every rule here unless the task explicitly overrides one.

> **This is a library, not a service.** It has no REST API, no deployment, no Helm chart, no database schema, and no dashboards of its own. The platform rules that govern those (endpoint design, OpenAPI/Postman, Liquibase, Helm, Terraform, per-endpoint alerting) do not apply here. They are restated below only where the library has to *support* a consuming service in meeting them: the error envelope, pool metrics, health checks, and PII-safe logging.

> **Who consumes it.** `xeno-ultima-quickfix` (`…/xeno-services/intersteller/xeno-ultima-quickfix`) and `xeno-orion-service`. Every change ships to both, so a behaviour change here is a behaviour change in production services you are not editing. Read §7 before changing anything a consumer can observe.

> **Repository.** `git@github.com:XENO-OS/multitenant-datasource-spring-boot-starter.git`, checked out at `…/xeno-services/libs/multitenant-datasource-spring-boot-starter`.
>
> | Branch | Line | Status |
> |---|---|---|
> | `main` | 3.x: Spring Boot 4, Java 25 | Active development |
> | `spring-boot-3.x` | 1.0.x: Spring Boot 3, Java 17 | End of life. No changes without tech lead sign-off |

---

## 1. Technology Stack

| Layer | Technology | Notes |
|---|---|---|
| Language | Java 25 | Gradle toolchain `languageVersion = 25`. Records, pattern matching, switch expressions and virtual threads are all fair game |
| Framework | Spring Boot 4.1.x (Spring Framework 7) | Boot's modular auto-configuration: JDBC classes live in `org.springframework.boot.jdbc.autoconfigure`, health in `org.springframework.boot.health.*` |
| Build | Gradle 9.8 wrapper | Always use `./gradlew`, never a system Gradle |
| Pooling | HikariCP (managed by the Boot BOM) | Never pin a Hikari, H2 or other BOM-managed version in `build.gradle` |
| Boilerplate | Lombok | `@Getter`/`@Setter` on properties, `@Slf4j`. Lombok runs before the configuration processor (order matters in `build.gradle`) |
| Tests | JUnit 5, AssertJ, Mockito, H2, Spring Boot test support | No Testcontainers dependency today; H2 in MySQL mode stands in for MySQL |
| Publishing | `maven-publish` over HTTPS (nginx WebDAV) | `https://repo.myxeno.com/m2/{releases,snapshots}` (§8) |

**Dependency rules: the published POM is part of the API.**
- `api`: only `spring-boot-starter-jdbc`. Anything added here becomes a transitive dependency of every consumer.
- `compileOnly` + `@ConditionalOnClass`: every optional integration (Spring MVC/servlet, `spring-boot-health`, Micrometer). The consuming application supplies it; the auto-configuration switches on only when it's present.
- **Never add `spring-boot-starter-web`/`-webmvc` as a runtime dependency.** Kafka workers and batch jobs use this library without Spring MVC (requirement 5.3). `MultitenantDatasourceAutoConfigurationTest.worksWithoutSpringMvcOnTheClasspath` guards this.
- Adding a dependency to `api` or `implementation` requires tech lead sign-off.

---

## 2. Architecture

### 2.1 Packages

| Package | Contents |
|---|---|
| `autoconfigure` | The three auto-configurations and `TenantProperties` (`multitenancy.*`) |
| `config` | `TenantRoutingDataSource`, the `AbstractRoutingDataSource` that looks the current tenant's pool up in the provider |
| `context` | `TenantContext` (thread-bound tenant, `runAs`, propagation) and `TenantTaskDecorator` |
| `provider` | `TenantDataSourceProvider` (pool registry and lifecycle), `PoolSettings`, the `TenantDataSourceConfigProvider` and `TenantDataSourceCustomizer` SPIs |
| `provider.impl` | Properties-backed (default) and database-backed config providers |
| `web` | `TenantResolutionFilter`, the servlet filter that resolves and validates the tenant |
| `health` | `TenantDataSourcesHealthIndicator` |
| `exception` | `TenantResolutionException` and its subclasses, `TenantDataSourceInitializationException` |

### 2.2 Auto-configuration invariants

These are load-bearing. Each exists because breaking it broke a consumer, and each has a test.

1. **`MultitenantDatasourceAutoConfiguration` runs `before = DataSourceAutoConfiguration`.** That is what makes Boot's default pool back off and Boot's `JdbcTemplate`, `NamedParameterJdbcTemplate` and `DataSourceTransactionManager` bind to our `dataSource` bean. Changing it to `after` brings back the `DataSourceAutoConfiguration` exclusion every consumer used to need.
2. **The primary `dataSource` bean is a `LazyConnectionDataSourceProxy` over `TenantRoutingDataSource`.** Don't expose the routing datasource as primary, and don't define templates or transaction managers in this library. Boot's own auto-configuration does that and backs off correctly for JPA users.
3. **The health indicator bean is named `dbHealthIndicator`.** Boot's `DataSourceHealthContributorAutoConfiguration` backs off for that name, which is how the per-tenant check replaces the broken `db` check. Renaming the bean brings the broken one back.
4. **Web pieces live only in `MultitenantDatasourceWebAutoConfiguration`,** guarded by `@ConditionalOnWebApplication(SERVLET)`. Never reference servlet or Spring MVC types from the core auto-configuration.
5. **Beans come from `@Bean` methods in auto-configurations, never from `@Component` scanning.** Consumers don't scan `com.xenotech.multitenant`, and `@ConditionalOnMissingBean` on a scanned component is unreliable.
6. **Nothing ships in `src/main/resources` except `META-INF`.** No `application.properties` or `application.yml`: a starter that sets properties overrides its consumers (the old jar set `spring.application.name`).
7. **Register new auto-configurations** in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

### 2.3 Tenant invariants

- **Tenant identifiers are case-insensitive, normalised to trimmed lower case** by `TenantContext.normalize` at every entry point: configuration keys, the request header, `TenantContext`, provider lookups. New entry points must normalise too.
- **`TenantRoutingDataSource` never caches tenants.** It asks `TenantDataSourceProvider` on every connection, so runtime `addTenantDataSource`/`removeTenantDataSource` take effect immediately. Don't reintroduce a copied target map.
- **`isWrapperFor`/`unwrap` must not throw when no tenant is bound.** Boot's metrics and health infrastructure probe the `DataSource` at startup.
- **Code outside a request uses `TenantContext.runAs`**, never a bare `setCurrentTenant`, because a missed `clear()` leaks a tenant onto a pooled thread.
- **Pools open at startup** (`new HikariDataSource(config)`), so bad configuration fails fast (`multitenancy.fail-fast`).
- **Every pool the provider opens, the provider closes**: on context shutdown, on replacement, on removal.

### 2.4 Configuration properties

- Everything lives under `multitenancy.*`, but **the Keycloak multitenancy starter also reads `multitenancy.http.*` and `multitenancy.tenants`** (quickfix uses both). Never claim those keys, and check `xeno-ultima-quickfix`'s `application.yml` before adding a new top-level key.
- Property keys are `kebab-case`. Timeouts are `java.time.Duration` (a unit, or plain milliseconds for backward compatibility).
- **The Javadoc on a properties field is its documentation:** the configuration processor turns it into IDE metadata. Every property field gets a one-sentence Javadoc. Defaults the processor can't infer go in `META-INF/additional-spring-configuration-metadata.json`.
- Per-tenant pool settings are nullable and fall back to `multitenancy.datasource-defaults`, then to HikariCP's own default. Keep new pool settings on `PoolSettings` so both levels get them.

---

## 3. Error Handling

The library raises exceptions; consuming services turn them into responses. The one exception is the tenant filter, which can respond on the service's behalf and must then follow the platform envelope.

- **Exception hierarchy.** All tenant failures extend `TenantResolutionException`: `TenantNotFoundException` (none supplied or bound) and `UnknownTenantException` (no datasource for it). Consumers catch the base class. Add new tenant failures as subclasses, never as unrelated exceptions.
- **Validate at resolution time, not at first query.** A failure during a query arrives wrapped in Spring's `CannotGetJdbcConnectionException`, which is why services used to walk cause chains. The filter rejects a missing or unknown tenant before application code runs.
- **Envelope (XENO backend standards §2.4).** With `multitenancy.resolution.error-response=envelope`, the filter writes the platform envelope, where `status` is the HTTP status name in `UPPER_SNAKE_CASE`, never the number:

  ```json
  { "status": "BAD_REQUEST", "message": "Unknown tenant: zz", "data": null }
  ```

- Messages are user-facing: no stack traces, class names, JDBC URLs, or credentials.
- CORS preflights and excluded paths (default `/actuator/**`) never need a tenant.

---

## 4. Naming Conventions

| Element | Convention | Example |
|---|---|---|
| Configuration properties | `kebab-case` under `multitenancy` | `multitenancy.resolution.header-name` |
| Tenant identifiers | lower case after normalisation | `ug`, `ke`, `ng` |
| Pool names | `<spring.application.name>-<tenant>` | `orion-ug` |
| Enum values | `UPPER_SNAKE_CASE`; bound from properties as `kebab-case` | `SEND_ERROR` ↔ `send-error` |
| Java packages | lowercase, no separators | `com.xenotech.multitenant.datasource.provider` |
| Java classes | `PascalCase`, prefixed `Tenant` or `Multitenant` for public API | `TenantDataSourceProvider` |
| Java methods & fields | `camelCase` | `addTenantDataSource`, `failFast` |
| Test classes | `<ClassUnderTest>Test` | `TenantRoutingDataSourceTest` |

---

## 5. Code Documentation

Every public class and public method has a Javadoc comment. This is a library: the Javadoc is what consumers read in their IDE, and outdated Javadoc is a bug.

- **Class-level Javadoc** says what the type is for and any invariant a caller must respect (thread-safety, lifecycle, "must not throw without a tenant"). Add `@since` to public types and methods introduced after 3.0.0.
- **Method-level Javadoc** documents parameters, return value, and every exception a caller should handle (`@throws UnknownTenantException if…`).
- **Comments explain *why*, not *what*.** The non-obvious ordering and naming rules in §2.2 get a comment where they're implemented.
- **Deprecate before removing:** `@Deprecated(since = "x.y.z")` plus a Javadoc `@deprecated` pointing to the replacement. Removal happens in the next major version (§7).
- **Keep `README.md` in step with the code:** the property tables, the compatibility table, and the migration section for any breaking change.

---

## 6. Testing Requirements

A task is complete only when the full suite passes with zero failures:

```bash
./gradlew clean build
```

Gradle needs a JDK 17+ to run and provisions nothing itself, so point `JAVA_HOME` at a JDK 25 (locally: `~/.sdkman/candidates/java/25.0.1-zulu`).

**Test layers.** Use the lowest one that proves the behaviour:

| Layer | Tool | Use for |
|---|---|---|
| Unit | plain JUnit + AssertJ | `TenantContext`, `TenantDataSourceProvider` merging and lifecycle, routing |
| Auto-configuration | `ApplicationContextRunner` / `WebApplicationContextRunner` with Boot's **real** JDBC, health, transaction and task auto-configurations | Bean wiring, back-off, ordering, conditions, classpath variations (`FilteredClassLoader`) |
| Web | `@SpringBootTest` + `@AutoConfigureMockMvc` + actuator (`WebTestApplication`) | Filter behaviour over HTTP, `/actuator/health`, `/actuator/metrics` |

**Rules**
- **Every bug fix ships with a test that fails without the fix.** Prove it: revert the fix, watch the test fail, restore. A test that passes either way is not a regression test.
- **Every invariant in §2.2 and §2.3 has a test.** If you change one on purpose, change its test in the same commit and say why in the PR.
- **Each test gets its own H2 databases** via `TestTenants.h2(...)`/`h2Url(...)` (random suffix). Never share a named in-memory database between test classes.
- **Test against Boot's own auto-configuration, never a hand-built stand-in.** The ordering and back-off bugs this library has had were only visible with the real ones on the classpath.
- No external systems. Consumer-side Testcontainers coverage is tracked as follow-up requirement 6.3.

---

## 7. Compatibility and Versioning

Releases follow **semantic versioning, `major.minor.patch`**:

| Change | Version bump |
|---|---|
| Bug fix, no observable behaviour change for correct callers | patch |
| New property, SPI, or API; new optional integration | minor |
| Removed or renamed public type, method or property; changed default (pool sizes, fail-fast, error response); new Java or Spring Boot baseline; any change a consumer must act on | **major** |

- **A changed default is a breaking change** even if no signature changes. The 3.0.0 pool defaults (30/5 → 10/0) are the reference case.
- Every breaking change gets a README **migration** entry saying what a consumer must do, and where possible which workaround it lets them delete.
- Update the README **compatibility table** for every minor or major release.
- The `spring-boot-3.x` line is end of life. Security fixes there need tech lead sign-off, and ship as a patch on that branch.

---

## 8. Release Process

All changes reach `main` through a pull request with an approving review and green CI (GitHub Actions `build.yml`, JDK 25). Never push directly to `main`.

1. **Release commit on the PR:** set `version = 'X.Y.Z'` in `build.gradle` (no `-SNAPSHOT`) and update the README install snippets and compatibility table.
2. **After merge**, from an up-to-date `main`:
   ```bash
   ./gradlew clean build publish
   ```
   Publishing needs `mavenU`/`mavenP` from the environment or `~/.gradle/gradle.properties`. **Never commit credentials**, including placeholders, to the project's `gradle.properties`. The `myxeno` repository is registered only when both are present, so `publishToMavenLocal` works without them.
3. **Verify** the artifact resolves over HTTPS before announcing it:
   ```bash
   curl -sf https://repo.myxeno.com/m2/releases/com/xenotech/multitenant-datasource-spring-boot-starter/maven-metadata.xml
   ```
4. **Tag and release:** annotated tag `vX.Y.Z`, pushed, then a GitHub release with notes that lead with breaking changes.
5. **Return to a snapshot straight away:** commit `version = 'X.Y.(Z+1)-SNAPSHOT'` ("Prepare next development iteration"). `-SNAPSHOT` versions publish to `/m2/snapshots`. A released version number must never be left on a branch, because it can be republished over a released artifact.

The published jar is the plain jar with **no classifier** (`jar { archiveClassifier = '' }`). The Boot plugin defaults it to `-plain`, which leaves Maven consumers with no main artifact, so don't remove that line.

---

## 9. Security

- **Never log credentials:** no passwords, and no JDBC URLs that may embed them. Log the tenant id and pool name. Tenant ids (country codes) are not PII.
- **No credentials in `src/main`**, including in examples. An example `@Configuration` with a hardcoded password shipped in the jar until 3.0.0. Examples go in the README.
- The tenant header is untrusted input: it is normalised, checked against the configured tenants, and escaped before it appears in a JSON error body. Never use it to build SQL, file paths, or JDBC URLs.
- Pool metrics and health details carry tenant ids and pool names only, never connection strings.

---

## 10. Observability Support

The library doesn't run its own dashboards, but it must not stop a consuming service meeting the platform's observability standards:

- Every pool has a unique, stable name (`<app>-<tenant>`) so `hikaricp.connections.*{pool=…}` series and log lines can be told apart.
- With a `MeterRegistry` present, pools publish Micrometer metrics. The pools are not beans, so Boot never binds them on its own.
- The `db` health indicator reports each tenant separately. Services add `db` to their readiness group.
- Log at `debug`/`trace` for per-request routing; `info` only for lifecycle events (pools initialised, closed); `warn`/`error` only when something needs a human.
- Follow-up requirement 6.2 (tenant in the MDC and on the trace span) is not implemented yet. Don't half-implement it in passing.

---

## 11. Definition of Done

| # | Criterion |
|---|---|
| 1 | Change implements the requirement and has passed peer review on a PR |
| 2 | `./gradlew clean build` passes with zero failures on JDK 25, and CI is green |
| 3 | Every bug fix has a test proven to fail without the fix (§6) |
| 4 | New and modified public classes and methods have Javadoc; new properties have field Javadoc |
| 5 | No new `api`/`implementation` dependency without sign-off; optional integrations are `compileOnly` + `@ConditionalOnClass` (§1) |
| 6 | The §2.2 invariants still hold; `src/main/resources` still has no `application.*` |
| 7 | *(if applicable)* README updated: property tables, compatibility table, migration notes |
| 8 | *(if applicable)* Version bump matches §7; breaking changes called out in the PR description |
| 9 | Security checklist passed: no credentials in code, logs, or messages (§9) |
| 10 | *(if released)* Published, verified over HTTPS, tagged, GitHub release created, and branch moved to the next `-SNAPSHOT` (§8) |

---

## 12. Issue Scoring

Issues are scored with Fibonacci points mapped to **effort in hours**:

| Points | Effort |
|---|---|
| **0** | Parent / umbrella issue (no direct work) |
| **1** | ~2 hours |
| **2** | ~4 hours |
| **3** | ~8 hours |
| **5** | ~16 hours |
| **8** | ~32 hours |

Only `0, 1, 2, 3, 5, 8` are valid. Split anything at `5` or `8` into a `0`-point parent with independently deliverable, independently testable sub-issues. A library change that also needs consumer upgrades is a parent with one sub-issue per consuming service.
