package com.xenotech.multitenant.datasource.provider.impl;

import com.xenotech.multitenant.datasource.provider.TenantDataSourceConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DatabaseTenantDataSourceConfigProviderTest {

    @Mock
    private DataSource masterDataSource;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private DatabaseTenantDataSourceConfigProvider configProvider;

    private List<Map<String, Object>> tenantRows;
    private Map<String, Object> tenant1Row;
    private Map<String, Object> tenant2Row;

    @BeforeEach
    void setUp() {
        // Create provider and inject mocked JdbcTemplate
        configProvider = new DatabaseTenantDataSourceConfigProvider(masterDataSource);
        ReflectionTestUtils.setField(configProvider, "jdbcTemplate", jdbcTemplate);

        // Setup test data
        tenant1Row = new HashMap<>();
        tenant1Row.put("tenant_id", "tenant1");
        tenant1Row.put("url", "jdbc:h2:mem:tenant1;DB_CLOSE_DELAY=-1;MODE=MySQL");
        tenant1Row.put("username", "sa");
        tenant1Row.put("password", "");
        tenant1Row.put("driver_class_name", "org.h2.Driver");
        tenant1Row.put("maximum_pool_size", 10);
        tenant1Row.put("minimum_idle", 2);
        tenant1Row.put("connection_timeout", 5000L);
        tenant1Row.put("idle_timeout", 600000L);
        tenant1Row.put("max_lifetime", 1800000L);
        tenant1Row.put("pool_name", "Tenant1Pool");

        tenant2Row = new HashMap<>();
        tenant2Row.put("tenant_id", "tenant2");
        tenant2Row.put("url", "jdbc:h2:mem:tenant2;DB_CLOSE_DELAY=-1;MODE=MySQL");
        tenant2Row.put("username", "sa");
        tenant2Row.put("password", "");
        tenant2Row.put("driver_class_name", "org.h2.Driver");
        tenant2Row.put("maximum_pool_size", 30);
        tenant2Row.put("minimum_idle", 5);
        tenant2Row.put("connection_timeout", 3000L);
        tenant2Row.put("idle_timeout", 300000L);
        tenant2Row.put("max_lifetime", 1800000L);
        tenant2Row.put("pool_name", "Tenant2Pool");

        tenantRows = new ArrayList<>();
        tenantRows.add(tenant1Row);
        tenantRows.add(tenant2Row);
    }

    @Test
    void shouldGetAllTenantConfigurations() {
        // Arrange
        when(jdbcTemplate.queryForList(anyString())).thenReturn(tenantRows);

        // Act
        Map<String, TenantDataSourceConfigProvider.TenantDataSourceConfig> configs = configProvider.getTenantDataSourceConfigs();

        // Assert
        assertEquals(2, configs.size());
        assertTrue(configs.containsKey("tenant1"));
        assertTrue(configs.containsKey("tenant2"));

        TenantDataSourceConfigProvider.TenantDataSourceConfig config1 = configs.get("tenant1");
        assertEquals(tenant1Row.get("url"), config1.getUrl());
        assertEquals(tenant1Row.get("username"), config1.getUsername());
        assertEquals(tenant1Row.get("password"), config1.getPassword());
        assertEquals(tenant1Row.get("driver_class_name"), config1.getDriverClassName());
        assertEquals(tenant1Row.get("maximum_pool_size"), config1.getMaximumPoolSize());
        assertEquals(tenant1Row.get("minimum_idle"), config1.getMinimumIdle());
        assertEquals(tenant1Row.get("connection_timeout"), config1.getConnectionTimeout());
        assertEquals(tenant1Row.get("idle_timeout"), config1.getIdleTimeout());
        assertEquals(tenant1Row.get("max_lifetime"), config1.getMaxLifetime());
        assertEquals(tenant1Row.get("pool_name"), config1.getPoolName());

        TenantDataSourceConfigProvider.TenantDataSourceConfig config2 = configs.get("tenant2");
        assertEquals(tenant2Row.get("url"), config2.getUrl());
        assertEquals(tenant2Row.get("username"), config2.getUsername());
        assertEquals(tenant2Row.get("password"), config2.getPassword());
    }

    @Test
    void shouldGetSpecificTenantConfiguration() {
        // Arrange
        when(jdbcTemplate.queryForMap(anyString(), eq("tenant1"))).thenReturn(tenant1Row);

        // Act
        TenantDataSourceConfigProvider.TenantDataSourceConfig config = configProvider.getTenantDataSourceConfig("tenant1");

        // Assert
        assertNotNull(config);
        assertEquals(tenant1Row.get("url"), config.getUrl());
        assertEquals(tenant1Row.get("username"), config.getUsername());
        assertEquals(tenant1Row.get("password"), config.getPassword());
        assertEquals(tenant1Row.get("driver_class_name"), config.getDriverClassName());
        assertEquals(tenant1Row.get("maximum_pool_size"), config.getMaximumPoolSize());
        assertEquals(tenant1Row.get("minimum_idle"), config.getMinimumIdle());
        assertEquals(tenant1Row.get("connection_timeout"), config.getConnectionTimeout());
        assertEquals(tenant1Row.get("idle_timeout"), config.getIdleTimeout());
        assertEquals(tenant1Row.get("max_lifetime"), config.getMaxLifetime());
        assertEquals(tenant1Row.get("pool_name"), config.getPoolName());
    }

    @Test
    void shouldReturnNullForNonExistentTenant() {
        // Arrange
        when(jdbcTemplate.queryForMap(anyString(), eq("nonexistent")))
                .thenThrow(new EmptyResultDataAccessException(1));

        // Act
        TenantDataSourceConfigProvider.TenantDataSourceConfig config = configProvider.getTenantDataSourceConfig("nonexistent");

        // Assert
        assertNull(config);
    }
}
