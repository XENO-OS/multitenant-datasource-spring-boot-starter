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
     * 
     * @param tenantId The tenant identifier
     */
    public void addTenantDataSource(String tenantId) {
        TenantDataSourceConfigProvider.TenantDataSourceConfig config = 
            configProvider.getTenantDataSourceConfig(tenantId);
        if (config != null) {
            dataSources.put(tenantId, createDataSource(config));
        }
    }
}
