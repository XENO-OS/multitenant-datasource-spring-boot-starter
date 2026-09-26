package com.xenotech.multitenant.datasource.autoconfigure;

import com.xenotech.multitenant.datasource.config.TenantRoutingDataSource;
import com.xenotech.multitenant.datasource.context.TenantTaskDecorator;
import com.xenotech.multitenant.datasource.provider.TenantDataSourceConfigProvider;
import com.xenotech.multitenant.datasource.provider.TenantDataSourceCustomizer;
import com.xenotech.multitenant.datasource.provider.TenantDataSourceProvider;
import com.xenotech.multitenant.datasource.provider.impl.PropertiesTenantDataSourceConfigProvider;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;

/**
 * Registers one pool per tenant behind a routing {@link DataSource}.
 *
 * <p>Runs before Spring Boot's {@link DataSourceAutoConfiguration} so Boot's single default pool backs
 * off. Boot's {@code JdbcTemplate}, {@code NamedParameterJdbcTemplate} and {@code DataSourceTransactionManager}
 * auto-configuration then binds to the primary {@code dataSource} bean defined here.
 */
@AutoConfiguration(before = DataSourceAutoConfiguration.class)
@ConditionalOnClass({DataSource.class, AbstractRoutingDataSource.class, HikariDataSource.class})
@EnableConfigurationProperties(TenantProperties.class)
public class MultitenantDatasourceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TenantDataSourceConfigProvider tenantDataSourceConfigProvider(TenantProperties properties) {
        return new PropertiesTenantDataSourceConfigProvider(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantDataSourceProvider tenantDataSourceProvider(TenantDataSourceConfigProvider configProvider,
                                                             TenantProperties properties,
                                                             Environment environment,
                                                             ObjectProvider<TenantDataSourceCustomizer> customizers) {
        return new TenantDataSourceProvider(configProvider, properties.getDatasourceDefaults(), properties.isFailFast(),
                environment.getProperty("spring.application.name"), customizers.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantRoutingDataSource tenantRoutingDataSource(TenantDataSourceProvider provider) {
        return new TenantRoutingDataSource(provider);
    }

    /**
     * The application's {@link DataSource}: the routing datasource behind a lazy proxy, so a transaction
     * takes a connection at its first statement rather than when it begins.
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "dataSource")
    public LazyConnectionDataSourceProxy dataSource(TenantRoutingDataSource tenantRoutingDataSource) {
        return new LazyConnectionDataSourceProxy(tenantRoutingDataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantTaskDecorator tenantTaskDecorator() {
        return new TenantTaskDecorator();
    }

    /**
     * The tenant pools are not beans, so Boot's own Hikari metrics never see them; publish them directly.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class TenantDataSourceMetricsConfiguration {
        @Bean
        TenantDataSourceCustomizer tenantDataSourceMetricsCustomizer(ObjectProvider<MeterRegistry> meterRegistry) {
            return (tenant, config) -> {
                MeterRegistry registry = meterRegistry.getIfUnique();
                if (registry != null && config.getMetricsTrackerFactory() == null && config.getMetricRegistry() == null) {
                    config.setMetricsTrackerFactory(new MicrometerMetricsTrackerFactory(registry));
                }
            };
        }
    }
}
