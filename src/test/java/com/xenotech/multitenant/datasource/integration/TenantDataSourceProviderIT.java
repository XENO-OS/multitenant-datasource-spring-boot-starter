package com.xenotech.multitenant.datasource.integration;

import com.xenotech.multitenant.datasource.provider.TenantDataSourceConfigProvider;
import com.xenotech.multitenant.datasource.provider.TenantDataSourceProvider;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TenantDataSourceProviderIT {
    @InjectMocks
    private TenantDataSourceProvider provider;

    @Mock
    private TenantDataSourceConfigProvider configProvider;

    private TenantDataSourceConfigProvider.TenantDataSourceConfig tenant1Config;
    private TenantDataSourceConfigProvider.TenantDataSourceConfig tenant2Config;
    private Map<String, TenantDataSourceConfigProvider.TenantDataSourceConfig> tenantConfigs;

    @BeforeEach
    void setUp() {
        // Setup test tenant configurations
        tenant1Config = new TenantDataSourceConfigProvider.TenantDataSourceConfig();
        tenant1Config.setUrl("jdbc:h2:mem:tenant1;DB_CLOSE_DELAY=-1;MODE=MySQL");
        tenant1Config.setUsername("sa");
        tenant1Config.setPassword("");
        tenant1Config.setDriverClassName("org.h2.Driver");

        tenant2Config = new TenantDataSourceConfigProvider.TenantDataSourceConfig();
        tenant2Config.setUrl("jdbc:h2:mem:tenant2;DB_CLOSE_DELAY=-1;MODE=MySQL");
        tenant2Config.setUsername("sa");
        tenant2Config.setPassword("");
        tenant2Config.setDriverClassName("org.h2.Driver");

        // Create map of tenant configs
        tenantConfigs = new HashMap<>();
        tenantConfigs.put("tenant1", tenant1Config);
        tenantConfigs.put("tenant2", tenant2Config);

        // Mock config provider to return our test configs
        when(configProvider.getTenantDataSourceConfigs()).thenReturn(tenantConfigs);
        lenient().when(configProvider.getTenantDataSourceConfig("tenant1")).thenReturn(tenant1Config);
        lenient().when(configProvider.getTenantDataSourceConfig("tenant2")).thenReturn(tenant2Config);

        provider.initializeTenantDataSources();
    }
    @AfterEach
    void tearDown() {
        provider.getAllTenantDataSources().values().forEach(dataSource -> {
            if (dataSource instanceof HikariDataSource) {
                ((HikariDataSource) dataSource).close();
            }
        });
    }
    @Test
    void shouldInitializeDataSourcesForAllTenants() {
        // Act
        Map<Object, Object> dataSources = provider.getAllTenantDataSources();

        // Assert
        assertEquals(2, dataSources.size());
        assertTrue(dataSources.containsKey("tenant1"));
        assertTrue(dataSources.containsKey("tenant2"));

        DataSource ds1 = (DataSource) dataSources.get("tenant1");
        DataSource ds2 = (DataSource) dataSources.get("tenant2");

        assertInstanceOf(HikariDataSource.class, ds1);
        assertInstanceOf(HikariDataSource.class, ds2);
    }

    @Test
    void shouldConfigureDataSourcesWithCorrectProperties() {
        // Act
        Map<Object, Object> dataSources = provider.getAllTenantDataSources();
        HikariDataSource ds1 = (HikariDataSource) dataSources.get("tenant1");
        HikariDataSource ds2 = (HikariDataSource) dataSources.get("tenant2");

        // Assert
        assertEquals(tenant1Config.getUrl(), ds1.getJdbcUrl());
        assertEquals(tenant1Config.getUsername(), ds1.getUsername());
        assertEquals(tenant1Config.getPassword(), ds1.getPassword());

        assertEquals(tenant2Config.getUrl(), ds2.getJdbcUrl());
        assertEquals(tenant2Config.getUsername(), ds2.getUsername());
        assertEquals(tenant2Config.getPassword(), ds2.getPassword());
    }
    @Test
    void shouldCreateValidDataSources() throws SQLException {
        // Act
        Map<Object, Object> dataSources = provider.getAllTenantDataSources();

        // Assert - verify each data source can create connections
        dataSources.forEach((tenantId, dataSource) -> {
            assertInstanceOf(DataSource.class, dataSource);
            try (Connection connection = ((DataSource) dataSource).getConnection()) {
                assertNotNull(connection);
                assertFalse(connection.isClosed());
            } catch (SQLException e) {
                fail("Failed to get connection for tenant " + tenantId, e);
            }
        });
    }
}
