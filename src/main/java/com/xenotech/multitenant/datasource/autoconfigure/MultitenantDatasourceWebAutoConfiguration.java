package com.xenotech.multitenant.datasource.autoconfigure;

import com.xenotech.multitenant.datasource.provider.TenantDataSourceProvider;
import com.xenotech.multitenant.datasource.web.TenantResolutionFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the tenant of each HTTP request in a servlet filter. Only active in servlet applications,
 * so workers without Spring MVC can still use the routing datasource via {@code TenantContext.runAs}.
 */
@AutoConfiguration(after = MultitenantDatasourceAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(OncePerRequestFilter.class)
@ConditionalOnBean(TenantDataSourceProvider.class)
@ConditionalOnBooleanProperty(name = "multitenancy.resolution.enabled", matchIfMissing = true)
public class MultitenantDatasourceWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "tenantResolutionFilter")
    public FilterRegistrationBean<TenantResolutionFilter> tenantResolutionFilter(TenantDataSourceProvider provider,
                                                                                 TenantProperties properties) {
        TenantProperties.Resolution resolution = properties.getResolution();
        FilterRegistrationBean<TenantResolutionFilter> registration =
                new FilterRegistrationBean<>(new TenantResolutionFilter(provider, resolution));
        registration.setName("tenantResolutionFilter");
        registration.setOrder(resolution.getFilterOrder());
        return registration;
    }
}
