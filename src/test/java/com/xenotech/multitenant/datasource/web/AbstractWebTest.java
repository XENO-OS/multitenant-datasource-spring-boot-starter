package com.xenotech.multitenant.datasource.web;

import com.xenotech.multitenant.datasource.TestTenants;
import com.xenotech.multitenant.datasource.context.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

abstract class AbstractWebTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @DynamicPropertySource
    static void tenants(DynamicPropertyRegistry registry) {
        registry.add("spring.application.name", () -> "web-test");
        registry.add("multitenancy.datasources.ug.url", () -> TestTenants.h2Url("ug"));
        registry.add("multitenancy.datasources.ug.username", () -> "sa");
        registry.add("multitenancy.datasources.ke.url", () -> TestTenants.h2Url("ke"));
        registry.add("multitenancy.datasources.ke.username", () -> "sa");
        registry.add("management.endpoint.health.show-details", () -> "always");
        registry.add("management.endpoints.web.exposure.include", () -> "health,metrics");
    }

    @BeforeEach
    void createSchema() {
        for (String tenant : List.of("ug", "ke")) {
            TenantContext.runAs(tenant, () -> {
                jdbc.execute("CREATE TABLE IF NOT EXISTS country (code VARCHAR(2))");
                jdbc.execute("DELETE FROM country");
                jdbc.update("INSERT INTO country VALUES (?)", tenant);
            });
        }
    }
}
