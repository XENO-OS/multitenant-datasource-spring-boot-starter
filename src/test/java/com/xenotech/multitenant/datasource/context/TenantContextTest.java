package com.xenotech.multitenant.datasource.context;

import com.xenotech.multitenant.datasource.exception.TenantNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextTest {
    @AfterEach
    void tearDown() {
        TenantContext.clear(); // Clean up after each test
    }

    @Test
    void shouldSetAndGetCurrentTenant() {
        // Arrange
        String tenantId = "test-tenant";

        // Act
        TenantContext.setCurrentTenant(tenantId);

        // Assert
        assertEquals(tenantId, TenantContext.getCurrentTenant());
    }
    @Test
    void shouldReturnNullWhenNoTenantSet() {
        // Arrange - clear any existing tenant
        TenantContext.clear();

        // Act
        String tenant = TenantContext.getCurrentTenant();

        // Assert
        assertNull(tenant, "Expected current tenant to be null when no tenant is set");
    }
    @Test
    void shouldClearCurrentTenant() {
        // Arrange
        TenantContext.setCurrentTenant("test-tenant");

        // Act
        TenantContext.clear();

        // Assert
        assertNull(TenantContext.getCurrentTenant(), "Expected current tenant to be null after clear()");
    }
}