package com.xenotech.multitenant.datasource.health;

import com.xenotech.multitenant.datasource.provider.TenantDataSourceProvider;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Checks every tenant's pool with {@link Connection#isValid(int)}. Reports DOWN if any tenant's database
 * is unreachable, with a per-tenant breakdown in the details.
 *
 * <p>Registered as {@code dbHealthIndicator}, it replaces Spring Boot's {@code db} indicator, which
 * cannot check a routing datasource that has no default target.
 */
public class TenantDataSourcesHealthIndicator extends AbstractHealthIndicator {
    private final TenantDataSourceProvider provider;
    private final int validationTimeoutSeconds;

    public TenantDataSourcesHealthIndicator(TenantDataSourceProvider provider, Duration validationTimeout) {
        super("Tenant datasource health check failed");
        this.provider = provider;
        this.validationTimeoutSeconds = (int) Math.max(1, validationTimeout.toSeconds());
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        Map<String, Object> tenants = new LinkedHashMap<>();
        boolean allUp = true;
        for (Map.Entry<String, DataSource> entry : provider.getDataSources().entrySet()) {
            String error = check(entry.getValue());
            tenants.put(entry.getKey(), error == null ? "UP" : Map.of("status", "DOWN", "error", error));
            allUp &= error == null;
        }
        (allUp ? builder.up() : builder.down()).withDetail("tenants", tenants);
    }

    private String check(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(validationTimeoutSeconds) ? null : "Connection is not valid";
        } catch (Exception e) {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }
}
