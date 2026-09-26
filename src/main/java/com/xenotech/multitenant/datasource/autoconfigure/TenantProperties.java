package com.xenotech.multitenant.datasource.autoconfigure;

import com.xenotech.multitenant.datasource.provider.PoolSettings;
import com.xenotech.multitenant.datasource.provider.TenantDataSourceConfigProvider.TenantDataSourceConfig;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.core.Ordered;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration under {@code multitenancy}. Other libraries also use this prefix (the Keycloak
 * multitenancy starter reads {@code multitenancy.http} and {@code multitenancy.tenants}), so this class
 * only claims the keys below.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "multitenancy")
public class TenantProperties {

    /**
     * Whether to fail startup when a tenant's pool cannot be built. When false, the failure is logged
     * and the tenant is left out.
     */
    private boolean failFast = true;

    /**
     * Pool settings applied to every tenant unless the tenant overrides them.
     */
    @NestedConfigurationProperty
    private PoolSettings datasourceDefaults = defaultPoolSettings();

    /**
     * Datasource configuration per tenant, keyed by tenant identifier (case-insensitive).
     */
    private Map<String, TenantDataSourceConfig> datasources = new LinkedHashMap<>();

    /**
     * How the tenant is resolved for incoming HTTP requests.
     */
    private Resolution resolution = new Resolution();

    private static PoolSettings defaultPoolSettings() {
        PoolSettings defaults = new PoolSettings();
        defaults.setMaximumPoolSize(10);
        defaults.setMinimumIdle(0);
        defaults.setConnectionTimeout(Duration.ofSeconds(3));
        defaults.setIdleTimeout(Duration.ofMinutes(5));
        defaults.setMaxLifetime(Duration.ofMinutes(30));
        return defaults;
    }

    @Getter
    @Setter
    public static class Resolution {
        /**
         * Whether to register the servlet filter that resolves the tenant from a request header.
         */
        private boolean enabled = true;

        /**
         * Request header that carries the tenant identifier.
         */
        private String headerName = "X-Tenant-ID";

        /**
         * Order of the tenant filter. The default runs before Spring Security and other application filters.
         */
        private int filterOrder = Ordered.HIGHEST_PRECEDENCE + 50;

        /**
         * Ant-style request paths that need no tenant, relative to the context path.
         */
        private List<String> excludedPaths = new ArrayList<>(List.of("/actuator/**"));

        /**
         * Whether a request without a tenant header is rejected. Ignored when a default tenant is set.
         */
        private boolean required = true;

        /**
         * Tenant used when a request carries no tenant header.
         */
        private String defaultTenant;

        /**
         * What to do when a request's tenant is missing or unknown.
         */
        private ErrorResponse errorResponse = ErrorResponse.SEND_ERROR;
    }

    public enum ErrorResponse {
        /**
         * Respond 400 through the servlet container's error handling (Spring Boot's /error page).
         */
        SEND_ERROR,
        /**
         * Respond 400 with a JSON body {@code {"status": "BAD_REQUEST", "message": "...", "data": null}}.
         */
        ENVELOPE,
        /**
         * Rethrow the {@link com.xenotech.multitenant.datasource.exception.TenantResolutionException}
         * for an outer filter to handle.
         */
        THROW
    }
}
