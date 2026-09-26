package com.xenotech.multitenant.datasource.config;

import com.xenotech.multitenant.datasource.context.TenantContext;
import com.xenotech.multitenant.datasource.exception.TenantNotFoundException;
import com.xenotech.multitenant.datasource.exception.UnknownTenantException;
import com.xenotech.multitenant.datasource.provider.TenantDataSourceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Map;

/**
 * Routes each connection request to the pool of the tenant bound to the current thread.
 *
 * <p>Tenants are looked up in the {@link TenantDataSourceProvider} on every call rather than copied at
 * startup, so a tenant added or removed at runtime is routed correctly straight away.
 */
@Slf4j
public class TenantRoutingDataSource extends AbstractRoutingDataSource {
    private final TenantDataSourceProvider provider;

    public TenantRoutingDataSource(TenantDataSourceProvider provider) {
        this.provider = provider;
        // AbstractRoutingDataSource insists on a target map; lookups go to the provider instead
        super.setTargetDataSources(Map.of());
        super.setLenientFallback(false);
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.current()
                .orElseThrow(() -> new TenantNotFoundException("No tenant found in the current context."));
    }

    @Override
    protected DataSource determineTargetDataSource() {
        String tenant = (String) determineCurrentLookupKey();
        log.trace("Routing to tenant: {}", tenant);
        return provider.getDataSource(tenant).orElseThrow(() -> new UnknownTenantException(tenant));
    }

    /**
     * @return the current tenant pools, for tools (such as health checks) that inspect routing targets
     */
    @Override
    public Map<Object, DataSource> getResolvedDataSources() {
        return Map.copyOf(provider.getDataSources());
    }

    // AbstractRoutingDataSource delegates these to the current tenant's pool, which fails when
    // infrastructure code (metrics, health) probes the bean at startup with no tenant bound
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return true;
        }
        return TenantContext.current().isPresent() && determineTargetDataSource().isWrapperFor(iface);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        if (TenantContext.current().isEmpty()) {
            throw new SQLException("Cannot unwrap to " + iface.getName() + ": no tenant bound to the current thread");
        }
        return determineTargetDataSource().unwrap(iface);
    }
}
