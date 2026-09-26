# Multi-Tenant Datasource Spring Boot Starter

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Build Status](https://github.com/XENO-OS/multitenant-datasource-spring-boot-starter/actions/workflows/build.yml/badge.svg)](https://github.com/XENO-OS/multitenant-datasource-spring-boot-starter/actions)

A lightweight Spring Boot Starter for implementing database-level multi-tenancy with dynamic datasource routing.

## Features

- 🚀 **Automatic tenant detection** from HTTP headers
- 🔄 **Dynamic datasource switching** at runtime
- ⚡ **HikariCP connection pooling** for each tenant
- 🔒 **Thread-safe** tenant context management
- 🛠 **Simple configuration** via YAML/properties

## Installation

Add the dependency to your `build.gradle`:

```groovy
implementation 'com.xenotech:multitenant-datasource-spring-boot-starter:1.0.0'
```
Or for Maven:
```xml
<dependency>
<groupId>com.xenotech</groupId>
<artifactId>multitenant-datasource-spring-boot-starter</artifactId>
<version>1.0.0</version>
</dependency>
```
## Configuration
1. Add tenant configurations to application.yml:

```yaml
multitenancy:
  datasources:
    tenant1:
      url: jdbc:postgresql://localhost:5432/tenant1
      username: dbuser
      password: dbpass
      driver-class-name: org.postgresql.Driver
      maximum-pool-size: 15

    tenant2:
      url: jdbc:mysql://localhost:3306/tenant2
      username: dbuser
      password: dbpass
      driver-class-name: com.mysql.cj.jdbc.Driver
      minimum-idle: 3
```

## Usage
#### Automatic Tenant Resolution
The library automatically detects tenants from the X-Tenant-ID header:
```http
GET /api/data
X-Tenant-ID: tenant1
```
## Manual Tenant Switching
Programmatically set the tenant context:
```java
// Set tenant for current thread
TenantContext.setCurrentTenant("tenant2"); 

// Clear after operation
TenantContext.clear();
```

## Flexible Configuration Sources
The library supports reading tenant datasource configurations from different sources:

### Default: Properties-based Configuration
By default, tenant configurations are read from application properties (as shown in the Configuration section).

### Custom Configuration Sources
You can implement your own `TenantDataSourceConfigProvider` to read tenant configurations from any source:

1. Create a class that implements `TenantDataSourceConfigProvider`:
```java
@Component
public class CustomTenantDataSourceConfigProvider implements TenantDataSourceConfigProvider {
    @Override
    public Map<String, TenantDataSourceConfig> getTenantDataSourceConfigs() {
        // Your custom implementation to get all tenant configurations
    }

    @Override
    public TenantDataSourceConfig getTenantDataSourceConfig(String tenantId) {
        // Your custom implementation to get a specific tenant configuration
    }
}
```

2. Spring Boot will automatically use your implementation instead of the default one.

### Example: Database-based Configuration
An example implementation for reading tenant configurations from a database is provided:

```java
@Configuration
public class TenantDataSourceConfig {

    @Bean
    public TenantDataSourceConfigProvider tenantDataSourceConfigProvider(DataSource masterDataSource) {
        return new DatabaseTenantDataSourceConfigProvider(masterDataSource);
    }

    @Bean
    public DataSource masterDataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:postgresql://localhost:5432/master");
        dataSource.setUsername("master_user");
        dataSource.setPassword("master_pass");
        return dataSource;
    }
}
```

This assumes a database table structure like:
```sql
CREATE TABLE tenant_datasources (
    tenant_id VARCHAR(50) PRIMARY KEY,
    url VARCHAR(255) NOT NULL,
    username VARCHAR(100) NOT NULL,
    password VARCHAR(100) NOT NULL,
    driver_class_name VARCHAR(255) NOT NULL,
    maximum_pool_size INT DEFAULT 30,
    minimum_idle INT DEFAULT 5,
    connection_timeout BIGINT DEFAULT 3000,
    idle_timeout BIGINT DEFAULT 300000,
    max_lifetime BIGINT DEFAULT 1800000,
    pool_name VARCHAR(100) DEFAULT 'HikariConnPool'
);
```
## Accessing Tenant-Specific Data
Repository classes work normally - routing happens automatically:
```java
@Repository
public class ProductRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<Product> findAll() {
        // Automatically uses the correct datasource
        return jdbcTemplate.query("SELECT * FROM products", ...);
    }
}
```
## Building Locally
To build the project locally, run:
```bash
./gradlew clean build
```
## License
This project is licensed under the [MIT License](https://opensource.org/licenses/MIT) - see the [LICENSE](LICENSE) file for details.
