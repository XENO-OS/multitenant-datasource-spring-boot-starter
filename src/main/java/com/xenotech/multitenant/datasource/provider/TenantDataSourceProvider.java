package com.xenotech.multitenant.datasource.provider;

import com.xenotech.multitenant.datasource.autoconfigure.TenantProperties;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@EnableConfigurationProperties(TenantProperties.class)
public class TenantDataSourceProvider {
    private final Map<Object, Object> dataSources = new HashMap<>();
    private final TenantProperties tenantProperties;

    public TenantDataSourceProvider(TenantProperties tenantProperties) {
        this.tenantProperties = tenantProperties;
    }

    @PostConstruct
    public void initializeTenantDataSources() {
        tenantProperties.getDatasources().forEach((tenantId, tenantConfig) -> {
            try {
                log.debug("Initializing data source for tenant: {}", tenantId);
                dataSources.put(tenantId, createDataSource(tenantConfig));
            } catch (Exception e) {
                log.error("Failed to initialize database for tenant {}", tenantId, e);
            }
        });
    }

    public Map<Object, Object> getAllTenantDataSources() {
        return dataSources;
    }

    private DataSource createDataSource(TenantProperties.TenantConfig config) {
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
}
