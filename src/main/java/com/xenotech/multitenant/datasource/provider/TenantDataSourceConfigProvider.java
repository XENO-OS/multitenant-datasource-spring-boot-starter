package com.xenotech.multitenant.datasource.provider;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * Supplies the datasource configuration for each tenant. The default implementation reads
 * {@code multitenancy.datasources}; register your own bean to load tenants from elsewhere.
 */
public interface TenantDataSourceConfigProvider {

    /**
     * @return every tenant's configuration, keyed by tenant identifier (case-insensitive)
     */
    Map<String, TenantDataSourceConfig> getTenantDataSourceConfigs();

    /**
     * @return the tenant's configuration, or {@code null} if the tenant is not known
     */
    TenantDataSourceConfig getTenantDataSourceConfig(String tenantId);

    /**
     * Connection details for one tenant. Pool settings left unset fall back to {@code multitenancy.datasource-defaults}.
     */
    @Getter
    @Setter
    class TenantDataSourceConfig extends PoolSettings {
        /**
         * JDBC URL of the tenant's database.
         */
        private String url;

        /**
         * Database username.
         */
        private String username;

        /**
         * Database password.
         */
        private String password;

        /**
         * Pool name used in logs and metrics. Defaults to {@code <spring.application.name>-<tenant>}.
         */
        private String poolName;
    }
}
