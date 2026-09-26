package com.xenotech.multitenant.datasource.web;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TenantResolutionOptionsTest {

    @Nested
    @SpringBootTest(classes = WebTestApplication.class, properties = "multitenancy.resolution.error-response=envelope")
    @AutoConfigureMockMvc
    class Envelope extends AbstractWebTest {
        @Test
        void writesTheStandardEnvelope() throws Exception {
            mvc.perform(get("/tenant").header("X-Tenant-ID", "zz"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(content().json("{\"status\":400,\"message\":\"Unknown tenant: zz\",\"data\":null}"));
        }
    }

    @Nested
    @SpringBootTest(classes = WebTestApplication.class, properties = {
            "multitenancy.resolution.header-name=X-Country",
            "multitenancy.resolution.default-tenant=KE",
            "multitenancy.resolution.excluded-paths=/public/**"})
    @AutoConfigureMockMvc
    class CustomHeaderAndDefaultTenant extends AbstractWebTest {
        @Test
        void readsTheConfiguredHeader() throws Exception {
            mvc.perform(get("/tenant").header("X-Country", "UG"))
                    .andExpect(jsonPath("$.country").value("ug"));
        }

        @Test
        void fallsBackToTheDefaultTenant() throws Exception {
            mvc.perform(get("/tenant"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.country").value("ke"));
        }

        @Test
        void skipsExcludedPaths() throws Exception {
            mvc.perform(get("/public").header("X-Country", "ug"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("none"));
        }
    }

    @Nested
    @SpringBootTest(classes = WebTestApplication.class, properties = "multitenancy.resolution.required=false")
    @AutoConfigureMockMvc
    class Optional extends AbstractWebTest {
        @Test
        void letsRequestsWithoutATenantThrough() throws Exception {
            mvc.perform(get("/public"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("none"));
        }

        @Test
        void stillRejectsAnUnknownTenant() throws Exception {
            mvc.perform(get("/public").header("X-Tenant-ID", "zz"))
                    .andExpect(status().isBadRequest());
        }
    }
}
