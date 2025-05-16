package com.xenotech.multitenant.datasource.context.interceptors;

import com.xenotech.multitenant.datasource.context.TenantContext;
import com.xenotech.multitenant.datasource.exception.TenantNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantInterceptorTest {
    private TenantInterceptor interceptor;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Object handler;

    @BeforeEach
    void setUp() {
        interceptor = new TenantInterceptor();
    }
    @Test
    void shouldSetTenantFromHeader() throws Exception {
        // Arrange
        String tenantId = "tenant-1";
        when(request.getHeader("X-Tenant-ID")).thenReturn(tenantId);

        // Act
        boolean result = interceptor.preHandle(request, response, handler);

        // Assert
        assertTrue(result);
        assertEquals(tenantId, TenantContext.getCurrentTenant());
    }

    @Test
    void shouldClearTenantAfterCompletion() {
        // Arrange
        TenantContext.setCurrentTenant("test-tenant");

        // Act
        interceptor.afterCompletion(request, response, handler, null);

        // Assert
        assertThrows(TenantNotFoundException.class, TenantContext::getCurrentTenant);
    }

}