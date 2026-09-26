
# Modifying the Multitenant Datasource Starter for Flexible Configuration Sources

Based on the current implementation, I can suggest a design that allows reading tenant datasource configurations from different sources (config files, database, etc.) while maintaining backward compatibility.

## Proposed Solution

1. Create a `TenantDataSourceConfigProvider` interface that abstracts the source of tenant configurations
2. Provide a default implementation that reads from application properties (current behavior)
3. Allow custom implementations to be injected that could read from databases or other sources
4. Modify the existing `TenantDataSourceProvider` to use this interface

### Step 1: Create the Provider Interface

```java
package com.xenotech.multitenant.datasource.provider;

import java.util.Map;

/**
 * Interface for providing tenant datasource configurations from various sources.
 * Implementations can retrieve configurations from properties files, databases, etc.
 */
public interface TenantDataSourceConfigProvider {
    
    /**
     * Get all tenant configurations.
     * 
     * @return Map of tenant IDs to their datasource configurations
     */
    Map<String, TenantDataSourceConfig> getTenantDataSourceConfigs();
    
    /**
     * Get configuration for a specific tenant.
     * 
     * @param tenantId The tenant identifier
     * @return The datasource configuration for the tenant
     */
    TenantDataSourceConfig getTenantDataSourceConfig(String tenantId);
    
    /**
     * Class representing the configuration needed to create a datasource for a tenant.
     */
    class TenantDataSourceConfig {
        private String url;
        private String username;
        private String password;
        private String driverClassName;
        private int maximumPoolSize = 30;
        private int minimumIdle = 5;
        private long connectionTimeout = 3000;
        private long idleTimeout = 300000;
        private long maxLifetime = 1800000;
        private String poolName = "HikariConnPool";
        
        // Getters and setters
        // ...
    }
}
```

### Step 2: Create the Default Implementation

```java
package com.xenotech.multitenant.datasource.provider.impl;

import com.xenotech.multitenant.datasource.autoconfigure.TenantProperties;
import com.xenotech.multitenant.datasource.provider.TenantDataSourceConfigProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation that reads tenant configurations from application properties.
 */
@Component
@ConditionalOnMissingBean(TenantDataSourceConfigProvider.class)
public class PropertiesTenantDataSourceConfigProvider implements TenantDataSourceConfigProvider {
    
    private final TenantProperties tenantProperties;
    
    public PropertiesTenantDataSourceConfigProvider(TenantProperties tenantProperties) {
        this.tenantProperties = tenantProperties;
    }
    
    @Override
    public Map<String, TenantDataSourceConfig> getTenantDataSourceConfigs() {
        Map<String, TenantDataSourceConfig> configs = new HashMap<>();
        
        tenantProperties.getDatasources().forEach((tenantId, tenantConfig) -> {
            TenantDataSourceConfig config = new TenantDataSourceConfig();
            config.setUrl(tenantConfig.getUrl());
            config.setUsername(tenantConfig.getUsername());
            config.setPassword(tenantConfig.getPassword());
            config.setDriverClassName(tenantConfig.getDriverClassName());
            config.setMaximumPoolSize(tenantConfig.getMaximumPoolSize());
            config.setMinimumIdle(tenantConfig.getMinimumIdle());
            config.setConnectionTimeout(tenantConfig.getConnectionTimeout());
            config.setIdleTimeout(tenantConfig.getIdleTimeout());
            config.setMaxLifetime(tenantConfig.getMaxLifetime());
            config.setPoolName(tenantConfig.getPoolName());
            
            configs.put(tenantId, config);
        });
        
        return configs;
    }
    
    @Override
    public TenantDataSourceConfig getTenantDataSourceConfig(String tenantId) {
        TenantProperties.TenantConfig tenantConfig = tenantProperties.getDatasources().get(tenantId);
        if (tenantConfig == null) {
            return null;
        }
        
        TenantDataSourceConfig config = new TenantDataSourceConfig();
        config.setUrl(tenantConfig.getUrl());
        config.setUsername(tenantConfig.getUsername());
        config.setPassword(tenantConfig.getPassword());
        config.setDriverClassName(tenantConfig.getDriverClassName());
        config.setMaximumPoolSize(tenantConfig.getMaximumPoolSize());
        config.setMinimumIdle(tenantConfig.getMinimumIdle());
        config.setConnectionTimeout(tenantConfig.getConnectionTimeout());
        config.setIdleTimeout(tenantConfig.getIdleTimeout());
        config.setMaxLifetime(tenantConfig.getMaxLifetime());
        config.setPoolName(tenantConfig.getPoolName());
        
        return config;
    }
}
```

### Step 3: Modify TenantDataSourceProvider to Use the Interface

```java
package com.xenotech.multitenant.datasource.provider;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class TenantDataSourceProvider {
    private final Map<Object, Object> dataSources = new HashMap<>();
    private final TenantDataSourceConfigProvider configProvider;
    
    public TenantDataSourceProvider(TenantDataSourceConfigProvider configProvider) {
        this.configProvider = configProvider;
    }
    
    @PostConstruct
    public void initializeTenantDataSources() {
        configProvider.getTenantDataSourceConfigs().forEach((tenantId, config) -> {
            try {
                log.debug("Initializing data source for tenant: {}", tenantId);
                dataSources.put(tenantId, createDataSource(config));
            } catch (Exception e) {
                log.error("Failed to initialize database for tenant {}", tenantId, e);
            }
        });
    }
    
    public Map<Object, Object> getAllTenantDataSources() {
        return dataSources;
    }
    
    private DataSource createDataSource(TenantDataSourceConfigProvider.TenantDataSourceConfig config) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(config.getUrl());
        dataSource.setUsername(config.getUsername());
        dataSource.setPassword(config.getPassword());
        dataSource.setDriverClassName(config.getDriverClassName());
        dataSource.setMaximumPoolSize(config.getMaximumPoolSize());
        dataSource.setMinimumIdle(config.getMinimumIdle());
        dataSource.setConnectionTimeout(config.getConnectionTimeout());
        dataSource.setIdleTimeout(config.getIdleTimeout());
        dataSource.setMaxLifetime(config.getMaxLifetime());
        dataSource.setPoolName(config.getPoolName());
        return dataSource;
    }
    
    /**
     * Add a new tenant datasource dynamically
     */
    public void addTenantDataSource(String tenantId) {
        TenantDataSourceConfigProvider.TenantDataSourceConfig config = 
            configProvider.getTenantDataSourceConfig(tenantId);
        if (config != null) {
            dataSources.put(tenantId, createDataSource(config));
        }
    }
}
```

### Step 4: Update the Auto-Configuration

```java
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnClass({DataSource.class, AbstractRoutingDataSource.class})
@EnableConfigurationProperties(TenantProperties.class)
@Import({TenantDataSourceProvider.class, PropertiesTenantDataSourceConfigProvider.class})
public class MultitenantDatasourceAutoConfiguration implements WebMvcConfigurer {
    // Existing code...
}
```

## Example: Database-Based Implementation

Here's an example of how a custom implementation could read tenant configurations from a database:

```java
@Component
public class DatabaseTenantDataSourceConfigProvider implements TenantDataSourceConfigProvider {
    
    private final JdbcTemplate jdbcTemplate;
    
    public DatabaseTenantDataSourceConfigProvider(DataSource masterDataSource) {
        this.jdbcTemplate = new JdbcTemplate(masterDataSource);
    }
    
    @Override
    public Map<String, TenantDataSourceConfig> getTenantDataSourceConfigs() {
        Map<String, TenantDataSourceConfig> configs = new HashMap<>();
        
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT tenant_id, url, username, password, driver_class_name, " +
            "maximum_pool_size, minimum_idle, connection_timeout, " +
            "idle_timeout, max_lifetime, pool_name FROM tenant_datasources"
        );
        
        for (Map<String, Object> row : rows) {
            String tenantId = (String) row.get("tenant_id");
            TenantDataSourceConfig config = mapRowToConfig(row);
            configs.put(tenantId, config);
        }
        
        return configs;
    }
    
    @Override
    public TenantDataSourceConfig getTenantDataSourceConfig(String tenantId) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT url, username, password, driver_class_name, " +
                "maximum_pool_size, minimum_idle, connection_timeout, " +
                "idle_timeout, max_lifetime, pool_name FROM tenant_datasources " +
                "WHERE tenant_id = ?", tenantId
            );
            
            return mapRowToConfig(row);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
    
    private TenantDataSourceConfig mapRowToConfig(Map<String, Object> row) {
        TenantDataSourceConfig config = new TenantDataSourceConfig();
        config.setUrl((String) row.get("url"));
        config.setUsername((String) row.get("username"));
        config.setPassword((String) row.get("password"));
        config.setDriverClassName((String) row.get("driver_class_name"));
        config.setMaximumPoolSize((Integer) row.get("maximum_pool_size"));
        config.setMinimumIdle((Integer) row.get("minimum_idle"));
        config.setConnectionTimeout((Long) row.get("connection_timeout"));
        config.setIdleTimeout((Long) row.get("idle_timeout"));
        config.setMaxLifetime((Long) row.get("max_lifetime"));
        config.setPoolName((String) row.get("pool_name"));
        return config;
    }
}
```

## Usage

To use the default implementation (properties-based):
- No changes needed, works as before

To use a custom implementation (e.g., database-based):
1. Create a bean that implements `TenantDataSourceConfigProvider`
2. Spring Boot will automatically use your implementation instead of the default one

```java
@Configuration
public class TenantDataSourceConfig {
    
    @Bean
    public TenantDataSourceConfigProvider tenantDataSourceConfigProvider(DataSource masterDataSource) {
        return new DatabaseTenantDataSourceConfigProvider(masterDataSource);
    }
    
    // You'll need a master datasource to connect to the database that stores tenant configurations
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

This design maintains backward compatibility while providing flexibility to read tenant configurations from any source.