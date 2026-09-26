package com.xenotech.multitenant.datasource.web;

import com.xenotech.multitenant.datasource.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@SpringBootConfiguration
@EnableAutoConfiguration
class WebTestApplication {

    /**
     * An ordinary application filter (logging, security helpers) registered at the default order.
     * It records the tenant it sees in a response header.
     */
    @Bean
    FilterRegistrationBean<OncePerRequestFilter> tenantObservingFilter() {
        return new FilterRegistrationBean<>(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                String header = isAsyncDispatch(request) ? "X-Seen-Tenant-Async" : "X-Seen-Tenant";
                response.setHeader(header, TenantContext.current().orElse("none"));
                chain.doFilter(request, response);
            }

            @Override
            protected boolean shouldNotFilterAsyncDispatch() {
                return false;
            }
        });
    }

    @RestController
    static class TenantController {
        private final JdbcTemplate jdbc;

        TenantController(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @GetMapping("/tenant")
        Map<String, Object> tenant(@RequestHeader(name = "X-Tenant-ID", required = false) String header) {
            return Map.of(
                    "tenant", TenantContext.current().orElse("none"),
                    "header", header == null ? "none" : header,
                    "country", jdbc.queryForObject("SELECT code FROM country", String.class));
        }

        @GetMapping("/async")
        java.util.concurrent.Callable<String> async() {
            return () -> jdbc.queryForObject("SELECT code FROM country", String.class);
        }

        @GetMapping("/deferred")
        org.springframework.web.context.request.async.DeferredResult<String> deferred() {
            var result = new org.springframework.web.context.request.async.DeferredResult<String>();
            Thread.ofVirtual().start(() -> result.setResult("done"));
            return result;
        }

        @GetMapping("/public")
        String open() {
            return TenantContext.current().orElse("none");
        }
    }
}
