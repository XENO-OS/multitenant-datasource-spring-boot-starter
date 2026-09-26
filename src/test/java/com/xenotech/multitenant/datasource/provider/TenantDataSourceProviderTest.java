package com.xenotech.multitenant.datasource.provider;

import com.xenotech.multitenant.datasource.TestTenants;
import com.xenotech.multitenant.datasource.exception.TenantDataSourceInitializationException;
import com.xenotech.multitenant.datasource.exception.UnknownTenantException;
import com.xenotech.multitenant.datasource.provider.TenantDataSourceConfigProvider.TenantDataSourceConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantDataSourceProviderTest {
    private final Map<String, TenantDataSourceConfig> configs = new LinkedHashMap<>();
    private TenantDataSourceProvider provider;

    @AfterEach
    void tearDown() {
        if (provider != null) {
            provider.destroy();
        }
    }

    @Test
    void mergesTenantSettingsOverDefaults() {
        PoolSettings defaults = new PoolSettings();
        defaults.setMaximumPoolSize(10);
        defaults.setMinimumIdle(0);
        defaults.setLeakDetectionThreshold(Duration.ofSeconds(20));
        defaults.setKeepaliveTime(Duration.ofMinutes(2));
        defaults.setValidationTimeout(Duration.ofSeconds(4));
        defaults.setInitializationFailTimeout(Duration.ofMillis(-1));
        defaults.getDataSourceProperties().put("connectionAttributes", "program_name:orion");
        defaults.getDataSourceProperties().put("connectionTimeZone", "Africa/Kampala");
        TenantDataSourceConfig ug = TestTenants.h2("ug");
        ug.setMaximumPoolSize(4);
        ug.setLeakDetectionThreshold(Duration.ofSeconds(30));
        ug.getDataSourceProperties().put("connectionTimeZone", "UTC");

        HikariConfig config = provider(defaults, true).createHikariConfig("ug", ug);

        assertThat(config.getMaximumPoolSize()).isEqualTo(4);
        assertThat(config.getMinimumIdle()).isZero();
        assertThat(config.getLeakDetectionThreshold()).isEqualTo(30_000);
        assertThat(config.getKeepaliveTime()).isEqualTo(120_000);
        assertThat(config.getValidationTimeout()).isEqualTo(4_000);
        assertThat(config.getInitializationFailTimeout()).isEqualTo(-1);
        assertThat(config.getDataSourceProperties())
                .containsEntry("connectionAttributes", "program_name:orion")
                .containsEntry("connectionTimeZone", "UTC");
    }

    @Test
    void namesPoolsAfterTheApplicationAndTenantUnlessNamedExplicitly() {
        configs.put("ug", TestTenants.h2("ug"));
        TenantDataSourceConfig ke = TestTenants.h2("ke");
        ke.setPoolName("custom-ke");
        configs.put("ke", ke);

        provider = provider(new PoolSettings(), true);
        provider.afterPropertiesSet();

        assertThat(pool("ug").getPoolName()).isEqualTo("orion-ug");
        assertThat(pool("ke").getPoolName()).isEqualTo("custom-ke");
    }

    @Test
    void normalisesTenantKeys() {
        configs.put("UG", TestTenants.h2("ug"));

        provider = provider(new PoolSettings(), true);
        provider.afterPropertiesSet();

        assertThat(provider.getTenantIds()).containsExactly("ug");
        assertThat(provider.hasTenant("Ug")).isTrue();
    }

    @Test
    void failsFastAndClosesOpenedPoolsWhenATenantCannotBeBuilt() {
        configs.put("ug", TestTenants.h2("ug"));
        TenantDataSourceConfig broken = TestTenants.h2("ke");
        broken.setDriverClassName("com.example.MissingDriver");
        configs.put("ke", broken);
        provider = provider(new PoolSettings(), true);

        assertThatThrownBy(provider::afterPropertiesSet)
                .isInstanceOf(TenantDataSourceInitializationException.class)
                .hasMessageContaining("'ke'");
        assertThat(provider.getTenantIds()).isEmpty();
    }

    @Test
    void skipsABrokenTenantWhenFailFastIsOff() {
        configs.put("ug", TestTenants.h2("ug"));
        TenantDataSourceConfig broken = TestTenants.h2("ke");
        broken.setDriverClassName("com.example.MissingDriver");
        configs.put("ke", broken);

        provider = provider(new PoolSettings(), false);
        provider.afterPropertiesSet();

        assertThat(provider.getTenantIds()).containsExactly("ug");
    }

    @Test
    void appliesCustomizersBeforeThePoolOpens() {
        configs.put("ug", TestTenants.h2("ug"));
        TenantDataSourceCustomizer customizer = (tenant, config) -> config.setConnectionTestQuery("SELECT '" + tenant + "'");

        provider = new TenantDataSourceProvider(new MapConfigProvider(configs), new PoolSettings(), true, "orion", List.of(customizer));
        provider.afterPropertiesSet();

        assertThat(pool("ug").getConnectionTestQuery()).isEqualTo("SELECT 'ug'");
    }

    @Test
    void addsReplacesAndRemovesTenantsAtRuntime() {
        provider = provider(new PoolSettings(), true);
        provider.afterPropertiesSet();

        configs.put("ng", TestTenants.h2("ng"));
        provider.addTenantDataSource("NG");
        HikariDataSource first = pool("ng");
        provider.addTenantDataSource("ng", TestTenants.h2("ng"));
        HikariDataSource second = pool("ng");

        assertThat(second).isNotSameAs(first);
        assertThat(first.isClosed()).isTrue();

        provider.removeTenantDataSource("ng");
        assertThat(second.isClosed()).isTrue();
        assertThat(provider.hasTenant("ng")).isFalse();
        assertThatThrownBy(() -> provider.addTenantDataSource("zz")).isInstanceOf(UnknownTenantException.class);
    }

    @Test
    void closesEveryPoolOnDestroy() {
        configs.put("ug", TestTenants.h2("ug"));
        configs.put("ke", TestTenants.h2("ke"));
        provider = provider(new PoolSettings(), true);
        provider.afterPropertiesSet();
        HikariDataSource ug = pool("ug");
        HikariDataSource ke = pool("ke");

        provider.destroy();

        assertThat(ug.isClosed()).isTrue();
        assertThat(ke.isClosed()).isTrue();
        assertThat(provider.getTenantIds()).isEmpty();
    }

    private TenantDataSourceProvider provider(PoolSettings defaults, boolean failFast) {
        return new TenantDataSourceProvider(new MapConfigProvider(configs), defaults, failFast, "orion", List.of());
    }

    private HikariDataSource pool(String tenant) {
        return (HikariDataSource) provider.getDataSource(tenant).orElseThrow();
    }

    private record MapConfigProvider(Map<String, TenantDataSourceConfig> configs) implements TenantDataSourceConfigProvider {
        @Override
        public Map<String, TenantDataSourceConfig> getTenantDataSourceConfigs() {
            return configs;
        }

        @Override
        public TenantDataSourceConfig getTenantDataSourceConfig(String tenantId) {
            return configs.get(tenantId.toLowerCase());
        }
    }
}
