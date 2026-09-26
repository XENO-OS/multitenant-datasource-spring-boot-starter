package com.xenotech.multitenant.datasource.provider.impl;

import com.xenotech.multitenant.datasource.autoconfigure.TenantProperties;
import com.xenotech.multitenant.datasource.context.TenantContext;
import com.xenotech.multitenant.datasource.provider.TenantDataSourceConfigProvider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads tenants from {@code multitenancy.datasources}. Used unless the application defines its own
 * {@link TenantDataSourceConfigProvider}.
 */
public class PropertiesTenantDataSourceConfigProvider implements TenantDataSourceConfigProvider {
    private final TenantProperties tenantProperties;

    public PropertiesTenantDataSourceConfigProvider(TenantProperties tenantProperties) {
        this.tenantProperties = tenantProperties;
    }

    @Override
    public Map<String, TenantDataSourceConfig> getTenantDataSourceConfigs() {
        Map<String, TenantDataSourceConfig> configs = new LinkedHashMap<>();
        tenantProperties.getDatasources().forEach((tenantId, config) -> configs.put(TenantContext.normalize(tenantId), config));
        return configs;
    }

    @Override
    public TenantDataSourceConfig getTenantDataSourceConfig(String tenantId) {
        return getTenantDataSourceConfigs().get(TenantContext.normalize(tenantId));
    }
}
