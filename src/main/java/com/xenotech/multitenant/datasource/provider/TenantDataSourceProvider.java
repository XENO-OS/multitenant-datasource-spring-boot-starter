package com.xenotech.multitenant.datasource.provider;

import com.xenotech.multitenant.datasource.context.TenantContext;
import com.xenotech.multitenant.datasource.exception.TenantDataSourceInitializationException;
import com.xenotech.multitenant.datasource.exception.UnknownTenantException;
import com.xenotech.multitenant.datasource.provider.TenantDataSourceConfigProvider.TenantDataSourceConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Owns one HikariCP pool per tenant: builds them at startup, adds and removes them at runtime, and
 * closes them all when the application context closes. {@link com.xenotech.multitenant.datasource.config.TenantRoutingDataSource}
 * looks tenants up here on every connection, so changes take effect immediately.
 */
@Slf4j
public class TenantDataSourceProvider implements InitializingBean, DisposableBean {
    private final Map<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();
    private final TenantDataSourceConfigProvider configProvider;
    private final PoolSettings defaults;
    private final boolean failFast;
    private final String poolNamePrefix;
    private final List<TenantDataSourceCustomizer> customizers;

    /**
     * @param configProvider source of tenant configurations
     * @param defaults       pool settings applied where a tenant sets none
     * @param failFast       whether a pool that cannot be built fails startup, rather than being skipped
     * @param poolNamePrefix prefix of generated pool names ({@code <prefix>-<tenant>}), usually the application name
     * @param customizers    applied to each pool's configuration before it opens
     */
    public TenantDataSourceProvider(TenantDataSourceConfigProvider configProvider, PoolSettings defaults,
                                    boolean failFast, String poolNamePrefix,
                                    List<TenantDataSourceCustomizer> customizers) {
        this.configProvider = configProvider;
        this.defaults = defaults != null ? defaults : new PoolSettings();
        this.failFast = failFast;
        this.poolNamePrefix = StringUtils.hasText(poolNamePrefix) ? poolNamePrefix : "multitenant";
        this.customizers = List.copyOf(customizers);
    }

    @Override
    public void afterPropertiesSet() {
        initializeTenantDataSources();
    }

    public void initializeTenantDataSources() {
        configProvider.getTenantDataSourceConfigs().forEach((tenantId, config) -> {
            try {
                addTenantDataSource(tenantId, config);
            } catch (RuntimeException e) {
                if (failFast) {
                    destroy();
                    throw e;
                }
                log.error("Skipping tenant '{}': its datasource could not be initialised", tenantId, e);
            }
        });
        log.info("Initialised datasources for tenants {}", dataSources.keySet());
    }

    /**
     * @return the tenant's pool, if the tenant is known (case-insensitive)
     */
    public Optional<DataSource> getDataSource(String tenantId) {
        String key = TenantContext.normalize(tenantId);
        return key == null ? Optional.empty() : Optional.ofNullable(dataSources.get(key));
    }

    public boolean hasTenant(String tenantId) {
        return getDataSource(tenantId).isPresent();
    }

    public Set<String> getTenantIds() {
        return Collections.unmodifiableSet(dataSources.keySet());
    }

    /**
     * @return a read-only view of every tenant's pool
     */
    public Map<String, DataSource> getDataSources() {
        return Collections.unmodifiableMap(dataSources);
    }

    /**
     * @return a read-only snapshot of every tenant's pool, in the shape {@code AbstractRoutingDataSource} uses
     */
    public Map<Object, Object> getAllTenantDataSources() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(dataSources));
    }

    /**
     * Builds (or rebuilds) the tenant's pool from the {@link TenantDataSourceConfigProvider}.
     *
     * @throws UnknownTenantException if the provider has no configuration for the tenant
     */
    public void addTenantDataSource(String tenantId) {
        TenantDataSourceConfig config = configProvider.getTenantDataSourceConfig(tenantId);
        if (config == null) {
            throw new UnknownTenantException(TenantContext.normalize(tenantId));
        }
        addTenantDataSource(tenantId, config);
    }

    /**
     * Builds the tenant's pool from {@code config} and makes it available for routing straight away.
     * A pool previously registered for the tenant is replaced and closed.
     *
     * @throws TenantDataSourceInitializationException if the pool cannot be built
     */
    public void addTenantDataSource(String tenantId, TenantDataSourceConfig config) {
        String key = TenantContext.normalize(tenantId);
        Assert.hasText(key, "tenantId cannot be empty");
        HikariDataSource dataSource = createDataSource(key, config);
        HikariDataSource previous = dataSources.put(key, dataSource);
        log.debug("Registered datasource for tenant '{}' (pool '{}')", key, dataSource.getPoolName());
        close(key, previous);
    }

    /**
     * Stops routing to the tenant and closes its pool.
     */
    public void removeTenantDataSource(String tenantId) {
        String key = TenantContext.normalize(tenantId);
        if (key != null) {
            close(key, dataSources.remove(key));
        }
    }

    @Override
    public void destroy() {
        dataSources.forEach(this::close);
        dataSources.clear();
    }

    /**
     * Builds the pool configuration for a tenant: its own settings, then the defaults, then the customizers.
     */
    public HikariConfig createHikariConfig(String tenantId, TenantDataSourceConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.getUrl());
        hikari.setUsername(config.getUsername());
        hikari.setPassword(config.getPassword());
        hikari.setPoolName(StringUtils.hasText(config.getPoolName()) ? config.getPoolName() : poolNamePrefix + "-" + tenantId);
        apply(config, PoolSettings::getDriverClassName, hikari::setDriverClassName);
        apply(config, PoolSettings::getMaximumPoolSize, hikari::setMaximumPoolSize);
        apply(config, PoolSettings::getMinimumIdle, hikari::setMinimumIdle);
        applyMillis(config, PoolSettings::getConnectionTimeout, hikari::setConnectionTimeout);
        applyMillis(config, PoolSettings::getIdleTimeout, hikari::setIdleTimeout);
        applyMillis(config, PoolSettings::getMaxLifetime, hikari::setMaxLifetime);
        applyMillis(config, PoolSettings::getLeakDetectionThreshold, hikari::setLeakDetectionThreshold);
        applyMillis(config, PoolSettings::getKeepaliveTime, hikari::setKeepaliveTime);
        applyMillis(config, PoolSettings::getValidationTimeout, hikari::setValidationTimeout);
        applyMillis(config, PoolSettings::getInitializationFailTimeout, hikari::setInitializationFailTimeout);
        defaults.getDataSourceProperties().forEach(hikari::addDataSourceProperty);
        config.getDataSourceProperties().forEach(hikari::addDataSourceProperty);
        customizers.forEach(customizer -> customizer.customize(tenantId, hikari));
        return hikari;
    }

    private HikariDataSource createDataSource(String tenantId, TenantDataSourceConfig config) {
        try {
            // Opening the pool here (rather than on first use) surfaces bad configuration at startup
            return new HikariDataSource(createHikariConfig(tenantId, config));
        } catch (RuntimeException e) {
            throw new TenantDataSourceInitializationException(tenantId, e);
        }
    }

    private <T> void apply(TenantDataSourceConfig config, Function<PoolSettings, T> getter, Consumer<T> setter) {
        T value = getter.apply(config);
        if (value == null) {
            value = getter.apply(defaults);
        }
        if (value != null) {
            setter.accept(value);
        }
    }

    private void applyMillis(TenantDataSourceConfig config, Function<PoolSettings, Duration> getter, Consumer<Long> setter) {
        apply(config, getter, duration -> setter.accept(duration.toMillis()));
    }

    private void close(String tenantId, HikariDataSource dataSource) {
        if (dataSource == null) {
            return;
        }
        try {
            dataSource.close();
            log.debug("Closed datasource for tenant '{}'", tenantId);
        } catch (RuntimeException e) {
            log.warn("Failed to close datasource for tenant '{}'", tenantId, e);
        }
    }
}
