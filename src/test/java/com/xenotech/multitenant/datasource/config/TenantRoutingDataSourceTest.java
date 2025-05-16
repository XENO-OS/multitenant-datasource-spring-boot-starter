package com.xenotech.multitenant.datasource.config;

import com.xenotech.multitenant.datasource.context.TenantContext;
import com.xenotech.multitenant.datasource.exception.TenantNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TenantRoutingDataSourceTest {
    private TenantRoutingDataSource dataSource;

    @Mock
    private DataSource tenant1DataSource;

    @Mock
    private DataSource tenant2DataSource;

    @BeforeEach
    void setUp() {
        dataSource = new TenantRoutingDataSource();
        TenantContext.setCurrentTenant("default-tenant"); // Set a default tenant
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }
    @Test
    void shouldDetermineCurrentLookupKeyFromContext() {
        // Arrange
        String tenantId = "tenant-1";
        TenantContext.setCurrentTenant(tenantId);

        // Act
        Object result = dataSource.determineCurrentLookupKey();

        // Assert
        assertEquals(tenantId, result);
    }
    @Test
    void shouldThrowExceptionWhenNoTenantInContext() {
        // Arrange
        TenantContext.clear();

        // Act & Assert
        assertThrows(TenantNotFoundException.class, () -> {
            dataSource.determineCurrentLookupKey();
        });
    }
}