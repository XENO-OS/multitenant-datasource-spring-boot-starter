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