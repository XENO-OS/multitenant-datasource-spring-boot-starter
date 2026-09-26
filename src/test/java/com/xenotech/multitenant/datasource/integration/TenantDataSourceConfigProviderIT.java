package com.xenotech.multitenant.datasource.integration;

import com.xenotech.multitenant.datasource.provider.TenantDataSourceConfigProvider;
import com.xenotech.multitenant.datasource.provider.TenantDataSourceConfigProviderTestConfig;
import com.xenotech.multitenant.datasource.provider.TenantDataSourceConfigProviderTestConfig.TestTenantDataSourceConfigProvider;
import com.xenotech.multitenant.datasource.provider.TenantDataSourceProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class TenantDataSourceConfigProviderIT {

    @Spy
    private TestTenantDataSourceConfigProvider configProvider = new TestTenantDataSourceConfigProvider();

    @InjectMocks
    private TenantDataSourceProvider dataSourceProvider;

    @BeforeEach
    void setUp() {
        dataSourceProvider.initializeTenantDataSources();
    }

    @Test
    void shouldLoadTestTenantDataSourceConfigProvider() {
        // Verify that the test implementation is loaded
        assertNotNull(configProvider);
        assertTrue(configProvider instanceof TenantDataSourceConfigProviderTestConfig.TestTenantDataSourceConfigProvider);
    }

    @Test
    void shouldLoadTenantConfigsFromProperties() {
        // Verify that tenant configs are loaded from properties
        Map<String, TenantDataSourceConfigProvider.TenantDataSourceConfig> configs = configProvider.getTenantDataSourceConfigs();

        assertNotNull(configs);
        assertEquals(2, configs.size());
        assertTrue(configs.containsKey("tenant1"));
        assertTrue(configs.containsKey("tenant2"));

        // Verify tenant1 config
        TenantDataSourceConfigProvider.TenantDataSourceConfig tenant1Config = configs.get("tenant1");
        assertEquals("jdbc:h2:mem:tenant1;DB_CLOSE_DELAY=-1;MODE=MySQL", tenant1Config.getUrl());
        assertEquals("sa", tenant1Config.getUsername());
        assertEquals("", tenant1Config.getPassword());
    }

    @Test
    void shouldCreateDataSourcesFromConfigs() {
        // Verify that data sources are created from configs
        Map<Object, Object> dataSources = dataSourceProvider.getAllTenantDataSources();

        assertNotNull(dataSources);
        assertEquals(2, dataSources.size());
        assertTrue(dataSources.containsKey("tenant1"));
        assertTrue(dataSources.containsKey("tenant2"));
    }

    @Test
    void shouldGetSpecificTenantConfig() {
        // Verify that a specific tenant config can be retrieved
        TenantDataSourceConfigProvider.TenantDataSourceConfig config = configProvider.getTenantDataSourceConfig("tenant1");

        assertNotNull(config);
        assertEquals("jdbc:h2:mem:tenant1;DB_CLOSE_DELAY=-1;MODE=MySQL", config.getUrl());
        assertEquals("sa", config.getUsername());
        assertEquals("", config.getPassword());
    }

    @Test
    void shouldReturnNullForNonExistentTenant() {
        // Verify that null is returned for a non-existent tenant
        TenantDataSourceConfigProvider.TenantDataSourceConfig config = configProvider.getTenantDataSourceConfig("nonexistent");

        assertNull(config);
    }

    @Test
    void shouldAddTenantDataSourceDynamically() {
        // Get initial count of data sources
        int initialCount = dataSourceProvider.getAllTenantDataSources().size();

        // Add a new tenant data source (this will use the mocked config provider)
        dataSourceProvider.addTenantDataSource("tenant1");

        // Verify that the count hasn't changed (since tenant1 already exists)
        assertEquals(initialCount, dataSourceProvider.getAllTenantDataSources().size());

        // Note: We can't test adding a completely new tenant here because the
        // PropertiesTenantDataSourceConfigProvider only knows about tenants defined in properties
    }
}
