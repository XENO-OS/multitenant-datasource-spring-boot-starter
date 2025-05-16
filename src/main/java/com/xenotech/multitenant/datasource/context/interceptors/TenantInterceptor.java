package com.xenotech.multitenant.datasource.context.interceptors;

import com.xenotech.multitenant.datasource.context.TenantContext;
import com.xenotech.multitenant.datasource.exception.TenantNotFoundException;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * TenantInterceptor is a Spring MVC interceptor that handles tenant context management
 * based on HTTP request headers. It sets and clears the tenant context for each request
 * to ensure that the correct tenant-specific data source is used.
 */
@Component
@RequiredArgsConstructor
public class TenantInterceptor  implements HandlerInterceptor {
    public static final String TENANT_HEADER = "X-Tenant-ID";
    /**
     * Pre-handle method to extract the tenant ID from the request header and set it in the TenantContext.
     * This method is called before the request is processed by the controller.
     *
     * @param request  The HTTP request object.
     * @param response The HTTP response object.
     * @param handler  The handler (or controller) that will handle the request.
     * @return true to continue processing the request, false to abort the request processing.
     */
    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        // Extract tenant ID from the request header "X-Tenant-ID"
        String tenantId = request.getHeader(TENANT_HEADER);
        if (StringUtils.isNotBlank(tenantId)) {
            // Set the tenant ID in the TenantContext for use by the TenantRoutingDataSource
            TenantContext.setCurrentTenant(tenantId);
        }else{
            throw new TenantNotFoundException("No tenant found in the current context");
        }
        // Continue processing the request
        return true;
    }

    /**
     * After-completion method to clear the tenant context after the request has been processed.
     * This method is called after the request has been completed, allowing for cleanup.
     *
     * @param request  The HTTP request object.
     * @param response The HTTP response object.
     * @param handler  The handler (or controller) that handled the request.
     * @param ex       Any exception that was thrown during request processing.
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // Clear the tenant context to avoid potential leakage between requests
        TenantContext.clear();
    }

}
