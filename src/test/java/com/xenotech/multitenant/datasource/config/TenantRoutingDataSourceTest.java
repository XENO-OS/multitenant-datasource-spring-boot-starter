package com.xenotech.multitenant.datasource.config;

import com.xenotech.multitenant.datasource.TestTenants;
import com.xenotech.multitenant.datasource.context.TenantContext;
import com.xenotech.multitenant.datasource.exception.TenantNotFoundException;
import com.xenotech.multitenant.datasource.exception.UnknownTenantException;
import com.xenotech.multitenant.datasource.provider.PoolSettings;
import com.xenotech.multitenant.datasource.provider.TenantDataSourceConfigProvider.TenantDataSourceConfig;
import com.xenotech.multitenant.datasource.provider.TenantDataSourceProvider;
import com.xenotech.multitenant.datasource.provider.impl.PropertiesTenantDataSourceConfigProvider;
import com.xenotech.multitenant.datasource.autoconfigure.TenantProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantRoutingDataSourceTest {
    private TenantDataSourceProvider provider;
    private TenantRoutingDataSource routing;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        TenantProperties properties = new TenantProperties();
        properties.getDatasources().put("ug", TestTenants.h2("ug"));
        properties.getDatasources().put("KE", TestTenants.h2("ke"));
        provider = new TenantDataSourceProvider(new PropertiesTenantDataSourceConfigProvider(properties),
                new PoolSettings(), true, "test", List.of());
        provider.afterPropertiesSet();
        routing = new TenantRoutingDataSource(provider);
        routing.afterPropertiesSet();
        jdbc = new JdbcTemplate(routing);
        for (String tenant : List.of("ug", "ke")) {
            TenantContext.runAs(tenant, () -> {
                jdbc.execute("CREATE TABLE country (code VARCHAR(2))");
                jdbc.update("INSERT INTO country VALUES (?)", tenant);
            });
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        provider.destroy();
    }

    @Test
    void routesEachTenantToItsOwnDatabase() {
        assertThat(TenantContext.runAs("ug", () -> jdbc.queryForObject("SELECT code FROM country", String.class))).isEqualTo("ug");
        assertThat(TenantContext.runAs("ke", () -> jdbc.queryForObject("SELECT code FROM country", String.class))).isEqualTo("ke");
    }

    @Test
    void looksTenantsUpCaseInsensitively() {
        TenantContext.setCurrentTenant("ke");
        assertThat(routing.determineTargetDataSource()).isSameAs(provider.getDataSource("KE").orElseThrow());
        assertThat(provider.hasTenant("Ug")).isTrue();
    }

    @Test
    void failsClearlyWithoutATenant() {
        assertThatThrownBy(() -> routing.getConnection())
                .isInstanceOf(TenantNotFoundException.class)
                .hasMessage("No tenant found in the current context.");
    }

    @Test
    void failsClearlyForAnUnknownTenant() {
        TenantContext.setCurrentTenant("zz");

        assertThatThrownBy(() -> routing.getConnection())
                .isInstanceOf(UnknownTenantException.class)
                .hasMessage("Unknown tenant: zz");
    }

    @Test
    void routesToATenantAddedAtRuntime() {
        TenantDataSourceConfig ng = TestTenants.h2("ng");
        provider.addTenantDataSource("NG", ng);

        String result = TenantContext.runAs("ng", () -> jdbc.queryForObject("SELECT 'ng'", String.class));

        assertThat(result).isEqualTo("ng");
        assertThat(routing.getResolvedDataSources()).containsKeys("ug", "ke", "ng");
    }

    @Test
    void stopsRoutingToARemovedTenant() {
        provider.removeTenantDataSource("ke");

        TenantContext.setCurrentTenant("ke");
        assertThatThrownBy(() -> routing.getConnection()).isInstanceOf(UnknownTenantException.class);
    }

    @Test
    void canBeProbedWithoutATenant() throws Exception {
        assertThat(routing.isWrapperFor(HikariDataSource.class)).isFalse();
        assertThat(routing.isWrapperFor(TenantRoutingDataSource.class)).isTrue();
        assertThat(routing.unwrap(TenantRoutingDataSource.class)).isSameAs(routing);
    }
}
