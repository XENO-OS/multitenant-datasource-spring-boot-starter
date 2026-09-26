package com.xenotech.multitenant.datasource.autoconfigure;

import com.xenotech.multitenant.datasource.health.TenantDataSourcesHealthIndicator;
import com.xenotech.multitenant.datasource.provider.TenantDataSourceProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/**
 * Replaces Spring Boot's {@code db} health indicator with one that checks every tenant's pool. Boot's
 * indicator backs off when a bean named {@code dbHealthIndicator} exists; it is still switched on and
 * off with {@code management.health.db.enabled}.
 */
@AutoConfiguration(after = MultitenantDatasourceAutoConfiguration.class,
        beforeName = "org.springframework.boot.jdbc.autoconfigure.health.DataSourceHealthContributorAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
@ConditionalOnBean(TenantDataSourceProvider.class)
@ConditionalOnEnabledHealthIndicator("db")
public class MultitenantDatasourceHealthAutoConfiguration {

    @Bean(name = "dbHealthIndicator")
    @ConditionalOnMissingBean(name = "dbHealthIndicator")
    public TenantDataSourcesHealthIndicator dbHealthIndicator(TenantDataSourceProvider provider) {
        return new TenantDataSourcesHealthIndicator(provider, Duration.ofSeconds(2));
    }
}
