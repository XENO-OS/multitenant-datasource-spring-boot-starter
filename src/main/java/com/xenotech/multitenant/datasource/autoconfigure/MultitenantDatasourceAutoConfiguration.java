package com.xenotech.multitenant.datasource.autoconfigure;

import com.xenotech.multitenant.datasource.context.interceptors.TenantInterceptor;
import com.xenotech.multitenant.datasource.config.TenantRoutingDataSource;
import com.xenotech.multitenant.datasource.provider.TenantDataSourceProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.sql.DataSource;

@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnClass({DataSource.class, AbstractRoutingDataSource.class})
@EnableConfigurationProperties(TenantProperties.class)
@Import({TenantDataSourceProvider.class})
public class MultitenantDatasourceAutoConfiguration implements WebMvcConfigurer {

    @Bean
    @ConditionalOnMissingBean
    public TenantInterceptor tenantInterceptor() {
        return new TenantInterceptor();
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean
    public TenantRoutingDataSource tenantRoutingDataSource(TenantDataSourceProvider provider) {
        TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();
        routingDataSource.setTargetDataSources(provider.getAllTenantDataSources());
        return routingDataSource;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor());
    }
}
