package com.xenotech.multitenant.datasource.provider;

import com.xenotech.multitenant.datasource.provider.TenantDataSourceConfigProvider.TenantDataSourceConfig;
import com.xenotech.multitenant.datasource.provider.impl.PropertiesTenantDataSourceConfigProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.HashMap;
import java.util.Map;

@TestConfiguration
public class TenantDataSourceConfigProviderTestConfig {

    @Bean
    public TenantDataSourceConfigProvider tenantDataSourceConfigProvider() {
        return new TestTenantDataSourceConfigProvider();
    }

    /**
     * A test implementation of TenantDataSourceConfigProvider that returns predefined configurations
     * for testing purposes.
     */
    public static class TestTenantDataSourceConfigProvider implements TenantDataSourceConfigProvider {
        private final Map<String, TenantDataSourceConfig> configs = new HashMap<>();

        public TestTenantDataSourceConfigProvider() {
            // Setup test tenant configurations
            TenantDataSourceConfig tenant1Config = new TenantDataSourceConfig();
            tenant1Config.setUrl("jdbc:h2:mem:tenant1;DB_CLOSE_DELAY=-1;MODE=MySQL");
            tenant1Config.setUsername("sa");
            tenant1Config.setPassword("");
            tenant1Config.setDriverClassName("org.h2.Driver");
            tenant1Config.setMaximumPoolSize(10);
            tenant1Config.setMinimumIdle(2);
            tenant1Config.setConnectionTimeout(5000);
            tenant1Config.setIdleTimeout(600000);
            tenant1Config.setMaxLifetime(1800000);
            tenant1Config.setPoolName("Tenant1Pool");

            TenantDataSourceConfig tenant2Config = new TenantDataSourceConfig();
            tenant2Config.setUrl("jdbc:h2:mem:tenant2;DB_CLOSE_DELAY=-1;MODE=MySQL");
            tenant2Config.setUsername("sa");
            tenant2Config.setPassword("");
            tenant2Config.setDriverClassName("org.h2.Driver");

            configs.put("tenant1", tenant1Config);
            configs.put("tenant2", tenant2Config);
        }

        @Override
        public Map<String, TenantDataSourceConfig> getTenantDataSourceConfigs() {
            return configs;
        }

        @Override
        public TenantDataSourceConfig getTenantDataSourceConfig(String tenantId) {
            return configs.get(tenantId);
        }
    }
}