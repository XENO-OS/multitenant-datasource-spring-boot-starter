package com.xenotech.multitenant.datasource.provider;

import com.zaxxer.hikari.HikariConfig;

/**
 * Callback to adjust a tenant's pool configuration before the pool opens. Every bean of this type is
 * applied, in order, after the starter has applied the configured properties.
 */
@FunctionalInterface
public interface TenantDataSourceCustomizer {
    void customize(String tenant, HikariConfig config);
}
