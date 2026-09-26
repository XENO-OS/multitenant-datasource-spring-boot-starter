package com.xenotech.multitenant.datasource.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest(classes = WebTestApplication.class)
@AutoConfigureMockMvc
class TenantResolutionFilterTest extends AbstractWebTest {

    @Test
    void resolvesTheTenantCaseInsensitivelyAndRoutesToItsDatabase() throws Exception {
        mvc.perform(get("/tenant").header("X-Tenant-ID", "UG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant").value("ug"))
                .andExpect(jsonPath("$.header").value("ug"))
                .andExpect(jsonPath("$.country").value("ug"));

        mvc.perform(get("/tenant").header("X-Tenant-ID", "ke"))
                .andExpect(jsonPath("$.country").value("ke"));
    }

    @Test
    void laterFiltersSeeTheTenant() throws Exception {
        mvc.perform(get("/tenant").header("X-Tenant-ID", "Ke"))
                .andExpect(header().string("X-Seen-Tenant", "ke"));
    }

    @Test
    void rejectsAMissingTenantWith400() throws Exception {
        mvc.perform(get("/tenant"))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Missing required header X-Tenant-ID"));
    }

    @Test
    void rejectsAnUnknownTenantWith400() throws Exception {
        mvc.perform(get("/tenant").header("X-Tenant-ID", "zz"))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Unknown tenant: zz"));
    }

    @Test
    void actuatorNeedsNoTenantAndReportsEveryTenantDatabase() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.db.details.tenants.ug").value("UP"))
                .andExpect(jsonPath("$.components.db.details.tenants.ke").value("UP"));
    }

    @Test
    void letsCorsPreflightsThroughWithoutATenant() throws Exception {
        mvc.perform(options("/tenant")
                        .header("Origin", "https://app.example.com")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "X-Tenant-ID"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isNotEqualTo(400));
    }

    @Test
    void asyncControllersRunUnderTheTenant() throws Exception {
        MvcResult started = mvc.perform(get("/async").header("X-Tenant-ID", "KE"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().string("ke"));
    }

    @Test
    void asyncDispatchIsResolvedAgain() throws Exception {
        MvcResult started = mvc.perform(get("/deferred").header("X-Tenant-ID", "ug"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Seen-Tenant-Async", "ug"));
    }

    @Test
    void publishesPoolMetricsPerTenant() throws Exception {
        mvc.perform(get("/actuator/metrics/hikaricp.connections.max").param("tag", "pool:web-test-ug"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value").value(10.0));
        mvc.perform(get("/actuator/metrics/hikaricp.connections.max").param("tag", "pool:web-test-ke"))
                .andExpect(status().isOk());
    }

    @Test
    void theTenantIsClearedAfterTheRequest() throws Exception {
        mvc.perform(get("/tenant").header("X-Tenant-ID", "ug")).andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(com.xenotech.multitenant.datasource.context.TenantContext.current()).isEmpty();
        mvc.perform(get("/actuator/health")).andExpect(header().string("X-Seen-Tenant", "none"));
    }
}
