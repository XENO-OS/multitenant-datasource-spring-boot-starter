# Multi-Tenant Datasource Spring Boot Starter

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Build Status](https://github.com/XENO-OS/multitenant-datasource-spring-boot-starter/actions/workflows/build.yml/badge.svg)](https://github.com/XENO-OS/multitenant-datasource-spring-boot-starter/actions)

A Spring Boot starter for database-per-tenant multi-tenancy: one HikariCP pool per tenant behind a routing `DataSource`, with the tenant resolved from a request header or set programmatically.

## Compatibility

| Starter version | Spring Boot | Java | Branch | Status |
|-----------------|-------------|------|--------|--------|
| 3.0.x           | 4.x         | 25+  | `main`            | Supported |
| 2.0.x           | 4.x         | 17+  | –                 | Superseded by 3.0 |
| 1.0.x           | 3.x         | 17+  | `spring-boot-3.x` | End of life — final release, no further updates |

## Features

- **Tenant resolution in a servlet filter**: every later filter, controller and query sees the tenant
- **Case-insensitive tenants**: `UG` and `ug` select the same database
- **Clear failures**: a missing or unknown tenant is rejected with 400 before application code runs
- **Tenant context API**: `runAs`, `current()`, `required()`, and propagation to `@Async`, executors and virtual threads
- **Pools per tenant**: global defaults with per-tenant overrides, unique pool names, Micrometer metrics, closed on shutdown
- **Spring integration**: Boot's `JdbcTemplate`, `NamedParameterJdbcTemplate` and transaction manager bind to the routing datasource; a per-tenant `db` health check
- **Works without Spring MVC**: Kafka workers and batch jobs get the routing via `TenantContext.runAs`

## Installation

```groovy
implementation 'com.xenotech:multitenant-datasource-spring-boot-starter:3.0.0'
```

```xml
<dependency>
    <groupId>com.xenotech</groupId>
    <artifactId>multitenant-datasource-spring-boot-starter</artifactId>
    <version>3.0.0</version>
</dependency>
```

The artifact is published to `https://repo.myxeno.com/m2/releases`.

## Configuration

```yaml
multitenancy:
  datasources:
    ug:
      url: jdbc:mysql://db:3306/ultima_ug
      username: ${DB_USERNAME}
      password: ${DB_PASSWORD}
    ke:
      url: jdbc:mysql://db:3306/ultima_ke
      username: ${DB_USERNAME}
      password: ${DB_PASSWORD}
      maximum-pool-size: 15          # overrides the default for this tenant only
```

Nothing else is needed: the starter runs before Spring Boot's `DataSourceAutoConfiguration`, so Boot's own pool backs off and `spring.datasource.*` is not required. Tenant keys are case-insensitive.

### Pool settings

Every pool setting can be set once under `multitenancy.datasource-defaults` and overridden per tenant. Durations take a unit (`30s`, `5m`) or plain milliseconds.

| Property | Default |
|---|---|
| `driver-class-name` | inferred from the JDBC URL |
| `maximum-pool-size` | `10` |
| `minimum-idle` | `0` |
| `connection-timeout` | `3s` |
| `idle-timeout` | `5m` |
| `max-lifetime` | `30m` |
| `leak-detection-threshold` | HikariCP default (off) |
| `keepalive-time` | HikariCP default |
| `validation-timeout` | HikariCP default |
| `initialization-fail-timeout` | HikariCP default (1 ms: an unreachable database fails startup) |
| `data-source-properties.*` | none; a tenant's entries are merged over the defaults |
| `pool-name` (tenant only) | `<spring.application.name>-<tenant>` |

```yaml
multitenancy:
  datasource-defaults:
    leak-detection-threshold: 20s
    data-source-properties:
      connectionAttributes: program_name:${spring.application.name}
      connectionTimeZone: Africa/Kampala
```

For anything not covered by properties, register a `TenantDataSourceCustomizer` bean. It receives each tenant's `HikariConfig` before the pool opens:

```java
@Bean
TenantDataSourceCustomizer readOnlyReplicaForReports() {
    return (tenant, config) -> config.setReadOnly(tenant.equals("reports"));
}
```

### Startup failures

With `multitenancy.fail-fast: true` (the default), startup fails if any tenant's pool cannot be built. Set it to `false` to log the failure and leave that tenant out instead. To start while a database is unreachable, set `initialization-fail-timeout: -1` as well.

### Request resolution

| Property | Default | |
|---|---|---|
| `multitenancy.resolution.enabled` | `true` | Register the tenant filter (servlet applications only) |
| `multitenancy.resolution.header-name` | `X-Tenant-ID` | Header carrying the tenant |
| `multitenancy.resolution.filter-order` | `Ordered.HIGHEST_PRECEDENCE + 50` | Runs before Spring Security and application filters |
| `multitenancy.resolution.excluded-paths` | `/actuator/**` | Ant patterns that need no tenant |
| `multitenancy.resolution.required` | `true` | Reject requests with no tenant header |
| `multitenancy.resolution.default-tenant` | none | Tenant to use when the header is absent |
| `multitenancy.resolution.error-response` | `send-error` | How to reject a missing or unknown tenant (see below) |

`error-response` values:

- `send-error`: `400` through Spring Boot's error handling, like any other `/error` response.
- `envelope`: `400` with `{"status": 400, "message": "Unknown tenant: zz", "data": null}`.
- `throw`: rethrow the `TenantResolutionException` for an outer filter to handle.

Downstream code that reads the header sees the normalised (lower-case) value, and the resolved tenant is also available as the request attribute `TenantResolutionFilter.TENANT_ATTRIBUTE`.

> The Keycloak multitenancy starter also reads `multitenancy.http.*` and `multitenancy.tenants`. This starter doesn't use those keys, so both can share the `multitenancy` prefix.

## Usage

Queries through `JdbcTemplate`, `NamedParameterJdbcTemplate` or `@Transactional` go to the current tenant's database:

```java
@Repository
class ProductRepository {
    private final JdbcTemplate jdbc;

    ProductRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<Product> findAll() {
        return jdbc.query("SELECT * FROM products", productMapper);
    }
}
```

Boot's `spring.jdbc.template.query-timeout` sets the query timeout. The application `DataSource` is a `LazyConnectionDataSourceProxy`, so a transaction takes a connection only at its first statement.

### Outside a request

Schedulers, Kafka listeners and health checks should use `runAs`. It binds the tenant and always restores the thread afterwards:

```java
TenantContext.runAs("ug", () -> outbox.relayPending());
BigDecimal aum = TenantContext.runAs("ke", () -> reports.totalAum());
```

Reading the tenant:

```java
Optional<String> tenant = TenantContext.current();  // empty outside a tenant
String tenant = TenantContext.required();           // throws TenantNotFoundException
```

### Other threads

A `TaskDecorator` bean is registered, so `@Async` methods and Boot's `applicationTaskExecutor` (including virtual threads) run under the caller's tenant automatically. For your own executors:

```java
CompletableFuture.supplyAsync(this::load, TenantContext.propagating(executor));
executor.execute(TenantContext.wrap(() -> work()));
```

### Errors

All tenant failures extend `TenantResolutionException`:

- `TenantNotFoundException`: no tenant supplied or bound.
- `UnknownTenantException`: no datasource configured for the tenant. `getTenant()` returns it.

A failure during a query reaches your code wrapped in Spring's `CannotGetJdbcConnectionException`. HTTP requests are validated in the filter before that can happen.

## Health and metrics

The starter registers its per-tenant check as the `db` health indicator, replacing Spring Boot's, which can't check a routing datasource. It reports `DOWN` if any tenant's database is unreachable:

```json
"db": { "status": "UP", "details": { "tenants": { "ug": "UP", "ke": "UP" } } }
```

Add it to the readiness group if pods should stop receiving traffic when a tenant database is down:

```yaml
management:
  endpoint:
    health:
      group:
        readiness:
          include: readinessState,db
```

With Micrometer on the classpath, every pool publishes `hikaricp.connections.*` tagged `pool=<pool-name>`.

## Custom tenant sources

Tenants are read from `multitenancy.datasources` by default. To load them from elsewhere, define a `TenantDataSourceConfigProvider` bean:

```java
@Bean
TenantDataSourceConfigProvider tenantDataSourceConfigProvider(DataSource masterDataSource) {
    return new DatabaseTenantDataSourceConfigProvider(masterDataSource);
}
```

`DatabaseTenantDataSourceConfigProvider` reads a table like this (NULL columns fall back to `datasource-defaults`):

```sql
CREATE TABLE tenant_datasources (
    tenant_id          VARCHAR(50) PRIMARY KEY,
    url                VARCHAR(255) NOT NULL,
    username           VARCHAR(100) NOT NULL,
    password           VARCHAR(100) NOT NULL,
    driver_class_name  VARCHAR(255),
    maximum_pool_size  INT,
    minimum_idle       INT,
    connection_timeout BIGINT,
    idle_timeout       BIGINT,
    max_lifetime       BIGINT,
    pool_name          VARCHAR(100)
);
```

Tenants can be added or removed while the application runs. Routing picks up the change straight away:

```java
tenantDataSourceProvider.addTenantDataSource("ng");          // loads its config from the provider
tenantDataSourceProvider.removeTenantDataSource("ng");       // stops routing and closes the pool
```

## Migrating from 2.x (and 0.0.1-SNAPSHOT)

- **Java 25** is required.
- **Remove workarounds** the starter now covers:
  - `DataSourceAutoConfiguration` exclusions.
  - `management.health.db.enabled: false`.
  - Header-normalisation filters.
  - `JdbcTemplate` configuration classes.
  - Pool customizers that set names, sizes or metrics.
  - Per-tenant health indicators.
  - Exception handlers that walk the cause chain looking for an unknown tenant.
- **Pool defaults changed** from 30 max / 5 idle to 10 max / 0 idle, and pools are named `<app>-<tenant>` instead of `HikariConnPool`. Set `maximum-pool-size` explicitly where you relied on the old default.
- **`driver-class-name`** is no longer defaulted to MariaDB; it's inferred from the URL.
- **`TenantInterceptor` is gone.** The tenant is resolved by `TenantResolutionFilter` instead, and unknown tenants now get `400` instead of a `500` at the first query.
- **Tenant values are lower-cased.** `TenantContext` returns `ug` for a request sent with `UG`.
- **`TenantContext.getCurrentTenant()`** still works but is deprecated; use `current()` or `required()`.
- **`TenantRoutingDataSource.addDataSource`/`removeDataSource` moved** to `TenantDataSourceProvider.addTenantDataSource`/`removeTenantDataSource`.
- **`TenantDataSourceConfig` pool fields are nullable** so they can inherit defaults. Timeouts are `Duration`.
- **Pools open at startup** (previously on first use), so bad configuration fails fast.

## Building locally

```bash
./gradlew clean build
```

Publishing to `repo.myxeno.com` needs `mavenU`/`mavenP` in the environment or `~/.gradle/gradle.properties`:

```bash
./gradlew publish
```

## License
This project is licensed under the [MIT License](https://opensource.org/licenses/MIT) - see the [LICENSE](LICENSE) file for details.
