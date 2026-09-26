package com.xenotech.multitenant.datasource.provider.impl;

import com.xenotech.multitenant.datasource.autoconfigure.TenantProperties;
import com.xenotech.multitenant.datasource.provider.TenantDataSourceConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PropertiesTenantDataSourceConfigProviderTest {

    @Mock
    private TenantProperties tenantProperties;

    @InjectMocks
    private PropertiesTenantDataSourceConfigProvider configProvider;

    private TenantProperties.TenantConfig tenant1Config;
    private TenantProperties.TenantConfig tenant2Config;
    private Map<String, TenantProperties.TenantConfig> tenantConfigs;

    @BeforeEach
    void setUp() {
        // Setup test tenant configurations
        tenant1Config = new TenantProperties.TenantConfig();
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

        tenant2Config = new TenantProperties.TenantConfig();
        tenant2Config.setUrl("jdbc:h2:mem:tenant2;DB_CLOSE_DELAY=-1;MODE=MySQL");
        tenant2Config.setUsername("sa");
        tenant2Config.setPassword("");
        tenant2Config.setDriverClassName("org.h2.Driver");

        tenantConfigs = new HashMap<>();
        tenantConfigs.put("tenant1", tenant1Config);
        tenantConfigs.put("tenant2", tenant2Config);

        // Mock properties to return our test configs
        when(tenantProperties.getDatasources()).thenReturn(tenantConfigs);
    }

    @Test
    void shouldGetAllTenantConfigurations() {
        // Act
        Map<String, TenantDataSourceConfigProvider.TenantDataSourceConfig> configs = configProvider.getTenantDataSourceConfigs();

        // Assert
        assertEquals(2, configs.size());
        assertTrue(configs.containsKey("tenant1"));
        assertTrue(configs.containsKey("tenant2"));

        TenantDataSourceConfigProvider.TenantDataSourceConfig config1 = configs.get("tenant1");
        assertEquals(tenant1Config.getUrl(), config1.getUrl());
        assertEquals(tenant1Config.getUsername(), config1.getUsername());
        assertEquals(tenant1Config.getPassword(), config1.getPassword());
        assertEquals(tenant1Config.getDriverClassName(), config1.getDriverClassName());
        assertEquals(tenant1Config.getMaximumPoolSize(), config1.getMaximumPoolSize());
        assertEquals(tenant1Config.getMinimumIdle(), config1.getMinimumIdle());
        assertEquals(tenant1Config.getConnectionTimeout(), config1.getConnectionTimeout());
        assertEquals(tenant1Config.getIdleTimeout(), config1.getIdleTimeout());
        assertEquals(tenant1Config.getMaxLifetime(), config1.getMaxLifetime());
        assertEquals(tenant1Config.getPoolName(), config1.getPoolName());

        TenantDataSourceConfigProvider.TenantDataSourceConfig config2 = configs.get("tenant2");
        assertEquals(tenant2Config.getUrl(), config2.getUrl());
        assertEquals(tenant2Config.getUsername(), config2.getUsername());
        assertEquals(tenant2Config.getPassword(), config2.getPassword());
    }

    @Test
    void shouldGetSpecificTenantConfiguration() {
        // Act
        TenantDataSourceConfigProvider.TenantDataSourceConfig config = configProvider.getTenantDataSourceConfig("tenant1");

        // Assert
        assertNotNull(config);
        assertEquals(tenant1Config.getUrl(), config.getUrl());
        assertEquals(tenant1Config.getUsername(), config.getUsername());
        assertEquals(tenant1Config.getPassword(), config.getPassword());
        assertEquals(tenant1Config.getDriverClassName(), config.getDriverClassName());
        assertEquals(tenant1Config.getMaximumPoolSize(), config.getMaximumPoolSize());
        assertEquals(tenant1Config.getMinimumIdle(), config.getMinimumIdle());
        assertEquals(tenant1Config.getConnectionTimeout(), config.getConnectionTimeout());
        assertEquals(tenant1Config.getIdleTimeout(), config.getIdleTimeout());
        assertEquals(tenant1Config.getMaxLifetime(), config.getMaxLifetime());
        assertEquals(tenant1Config.getPoolName(), config.getPoolName());
    }

    @Test
    void shouldReturnNullForNonExistentTenant() {
        // Act
        TenantDataSourceConfigProvider.TenantDataSourceConfig config = configProvider.getTenantDataSourceConfig("nonexistent");

        // Assert
        assertNull(config);
    }
}