package com.xenotech.multitenant.datasource.web;

import com.xenotech.multitenant.datasource.autoconfigure.TenantProperties;
import com.xenotech.multitenant.datasource.context.TenantContext;
import com.xenotech.multitenant.datasource.exception.TenantNotFoundException;
import com.xenotech.multitenant.datasource.exception.TenantResolutionException;
import com.xenotech.multitenant.datasource.exception.UnknownTenantException;
import com.xenotech.multitenant.datasource.provider.TenantDataSourceProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Resolves the tenant from a request header and binds it to the request thread for the rest of the
 * filter chain, so later filters, controllers and the routing datasource all see it.
 *
 * <p>The tenant is validated here: a missing or unknown tenant is rejected with 400 before any
 * application code runs. The header value is case-insensitive; downstream code reading the header
 * sees the normalised (lower-case) value.
 */
@Slf4j
public class TenantResolutionFilter extends OncePerRequestFilter {
    /**
     * Request attribute holding the resolved tenant.
     */
    public static final String TENANT_ATTRIBUTE = TenantResolutionFilter.class.getName() + ".TENANT";

    private final TenantDataSourceProvider provider;
    private final TenantProperties.Resolution properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public TenantResolutionFilter(TenantDataSourceProvider provider, TenantProperties.Resolution properties) {
        this.provider = provider;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Browsers send CORS preflights without custom headers, so they can never carry the tenant
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return properties.getExcludedPaths().stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * Resolve again on async dispatch: it runs on a different thread from the original request.
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String headerName = properties.getHeaderName();
        String header = request.getHeader(headerName);
        String tenant = TenantContext.normalize(header);
        if (tenant == null) {
            tenant = TenantContext.normalize(properties.getDefaultTenant());
        }

        if (tenant == null) {
            if (properties.isRequired()) {
                reject(new TenantNotFoundException("Missing required header " + headerName), response);
            } else {
                chain.doFilter(request, response);
            }
            return;
        }
        if (!provider.hasTenant(tenant)) {
            reject(new UnknownTenantException(tenant), response);
            return;
        }

        HttpServletRequest resolved = header != null && !tenant.equals(header)
                ? new NormalizedTenantRequest(request, headerName, tenant)
                : request;
        resolved.setAttribute(TENANT_ATTRIBUTE, tenant);
        try {
            TenantContext.callAs(tenant, () -> {
                chain.doFilter(resolved, response);
                return null;
            });
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    private void reject(TenantResolutionException exception, HttpServletResponse response) throws IOException {
        log.debug("Rejecting request: {}", exception.getMessage());
        switch (properties.getErrorResponse()) {
            case THROW -> throw exception;
            case SEND_ERROR -> response.sendError(HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
            case ENVELOPE -> {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write("{\"status\":400,\"message\":\"" + escapeJson(exception.getMessage()) + "\",\"data\":null}");
            }
        }
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }

    /**
     * Presents the tenant header in its normalised form, leaving every other header untouched.
     */
    private static final class NormalizedTenantRequest extends HttpServletRequestWrapper {
        private final String headerName;
        private final String tenant;

        private NormalizedTenantRequest(HttpServletRequest request, String headerName, String tenant) {
            super(request);
            this.headerName = headerName;
            this.tenant = tenant;
        }

        @Override
        public String getHeader(String name) {
            return headerName.equalsIgnoreCase(name) ? tenant : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return headerName.equalsIgnoreCase(name) ? Collections.enumeration(List.of(tenant)) : super.getHeaders(name);
        }
    }
}
