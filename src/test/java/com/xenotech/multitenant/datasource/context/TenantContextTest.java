package com.xenotech.multitenant.datasource.context;

import com.xenotech.multitenant.datasource.exception.TenantNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void shouldThrowExceptionWhenNoTenantSet() {
        // Arrange - clear any existing tenant
        TenantContext.clear();

        // Act & Assert
        assertThrows(TenantNotFoundException.class, TenantContext::getCurrentTenant);
    }
    @Test
    void shouldClearCurrentTenant() {
        // Arrange
        TenantContext.setCurrentTenant("test-tenant");

        // Act
        TenantContext.clear();

        // Assert
        assertThrows(TenantNotFoundException.class, TenantContext::getCurrentTenant);
    }
}