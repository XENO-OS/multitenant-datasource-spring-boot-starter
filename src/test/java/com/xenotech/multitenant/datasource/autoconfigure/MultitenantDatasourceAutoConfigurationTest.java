package com.xenotech.multitenant.datasource.autoconfigure;

import com.xenotech.multitenant.datasource.TestTenants;
import com.xenotech.multitenant.datasource.config.TenantRoutingDataSource;
import com.xenotech.multitenant.datasource.context.TenantContext;
import com.xenotech.multitenant.datasource.exception.TenantDataSourceInitializationException;
import com.xenotech.multitenant.datasource.health.TenantDataSourcesHealthIndicator;
import com.xenotech.multitenant.datasource.provider.TenantDataSourceConfigProvider;
import com.xenotech.multitenant.datasource.provider.TenantDataSourceConfigProvider.TenantDataSourceConfig;
import com.xenotech.multitenant.datasource.provider.TenantDataSourceProvider;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.health.DataSourceHealthContributorAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class MultitenantDatasourceAutoConfigurationTest {
    private static final AutoConfigurations AUTO_CONFIGURATIONS = AutoConfigurations.of(
            MultitenantDatasourceAutoConfiguration.class,
            MultitenantDatasourceWebAutoConfiguration.class,
            MultitenantDatasourceHealthAutoConfiguration.class,
            DataSourceAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            TransactionAutoConfiguration.class,
            DataSourceHealthContributorAutoConfiguration.class,
            TaskExecutionAutoConfiguration.class);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AUTO_CONFIGURATIONS)
            .withPropertyValues(
                    "spring.application.name=orion",
                    "multitenancy.datasources.ug.url=" + TestTenants.h2Url("ug"),
                    "multitenancy.datasources.ug.username=sa",
                    "multitenancy.datasources.KE.url=" + TestTenants.h2Url("ke"),
                    "multitenancy.datasources.KE.username=sa");

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void replacesBootsDefaultPoolWithALazyRoutingDataSource() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            DataSource dataSource = context.getBean(DataSource.class);
            assertThat(dataSource).isInstanceOf(LazyConnectionDataSourceProxy.class);
            assertThat(((LazyConnectionDataSourceProxy) dataSource).getTargetDataSource())
                    .isSameAs(context.getBean(TenantRoutingDataSource.class));
            assertThat(context).doesNotHaveBean(HikariDataSource.class);
            assertThat(context.getBean(TenantDataSourceProvider.class).getTenantIds()).containsExactlyInAnyOrder("ug", "ke");
        });
    }

    @Test
    void bootsJdbcInfrastructureBindsToTheRoutingDataSource() {
        runner.withPropertyValues("spring.jdbc.template.query-timeout=7s").run(context -> {
            DataSource dataSource = context.getBean(DataSource.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            assertThat(jdbcTemplate.getDataSource()).isSameAs(dataSource);
            assertThat(jdbcTemplate.getQueryTimeout()).isEqualTo(7);
            assertThat(context.getBean(NamedParameterJdbcTemplate.class).getJdbcTemplate()).isSameAs(jdbcTemplate);
            assertThat(context.getBean(DataSourceTransactionManager.class).getDataSource()).isSameAs(dataSource);

            String tenant = TenantContext.runAs("UG", () -> jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));
            assertThat(tenant).isNotBlank();
        });
    }

    @Test
    void transactionsTakeAConnectionOnlyAtTheirFirstStatement() {
        runner.run(context -> {
            HikariDataSource ug = (HikariDataSource) context.getBean(TenantDataSourceProvider.class).getDataSource("ug").orElseThrow();
            TransactionTemplate transactions = context.getBean(TransactionTemplate.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            TenantContext.runAs("ug", () -> jdbc.queryForObject("SELECT 1", Integer.class)); // warm up the proxy's defaults

            TenantContext.runAs("ug", () -> transactions.executeWithoutResult(status -> {
                assertThat(ug.getHikariPoolMXBean().getActiveConnections()).isZero();
                jdbc.queryForObject("SELECT 1", Integer.class);
                assertThat(ug.getHikariPoolMXBean().getActiveConnections()).isEqualTo(1);
            }));
        });
    }

    @Test
    void usesSaferPoolDefaultsAndUniquePoolNames() {
        runner.run(context -> {
            HikariDataSource ug = (HikariDataSource) context.getBean(TenantDataSourceProvider.class).getDataSource("ug").orElseThrow();
            HikariDataSource ke = (HikariDataSource) context.getBean(TenantDataSourceProvider.class).getDataSource("ke").orElseThrow();
            assertThat(ug.getMaximumPoolSize()).isEqualTo(10);
            assertThat(ug.getMinimumIdle()).isZero();
            assertThat(ug.getPoolName()).isEqualTo("orion-ug");
            assertThat(ke.getPoolName()).isEqualTo("orion-ke");
            assertThat(ug.getDriverClassName()).isNull(); // inferred from the JDBC URL
        });
    }

    @Test
    void appliesGlobalDefaultsAndPerTenantOverrides() {
        runner.withPropertyValues(
                "multitenancy.datasource-defaults.maximum-pool-size=6",
                "multitenancy.datasource-defaults.leak-detection-threshold=20s",
                "multitenancy.datasource-defaults.data-source-properties.connectionTimeZone=Africa/Kampala",
                "multitenancy.datasources.ug.maximum-pool-size=3",
                "multitenancy.datasources.ug.connection-timeout=4000").run(context -> {
            TenantDataSourceProvider provider = context.getBean(TenantDataSourceProvider.class);
            HikariDataSource ug = (HikariDataSource) provider.getDataSource("ug").orElseThrow();
            HikariDataSource ke = (HikariDataSource) provider.getDataSource("ke").orElseThrow();
            assertThat(ug.getMaximumPoolSize()).isEqualTo(3);
            assertThat(ug.getConnectionTimeout()).isEqualTo(4000);
            assertThat(ke.getMaximumPoolSize()).isEqualTo(6);
            assertThat(ke.getLeakDetectionThreshold()).isEqualTo(20_000);
            assertThat(ke.getDataSourceProperties()).containsEntry("connectionTimeZone", "Africa/Kampala");
        });
    }

    @Test
    void publishesPoolMetricsWhenAMeterRegistryIsPresent() {
        runner.withBean(MeterRegistry.class, SimpleMeterRegistry::new).run(context -> {
            MeterRegistry registry = context.getBean(MeterRegistry.class);
            assertThat(registry.find("hikaricp.connections").tag("pool", "orion-ug").gauge()).isNotNull();
            assertThat(registry.find("hikaricp.connections").tag("pool", "orion-ke").gauge()).isNotNull();
        });
    }

    @Test
    void closesEveryPoolWhenTheContextCloses() {
        HikariDataSource[] pools = new HikariDataSource[1];
        runner.run(context -> pools[0] = (HikariDataSource) context.getBean(TenantDataSourceProvider.class).getDataSource("ug").orElseThrow());

        assertThat(pools[0].isClosed()).isTrue();
    }

    @Test
    void failsStartupWhenATenantPoolCannotBeBuilt() {
        runner.withPropertyValues("multitenancy.datasources.ke.driver-class-name=com.example.MissingDriver").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().isInstanceOf(ClassNotFoundException.class);
            assertThat(context.getStartupFailure()).hasStackTraceContaining(TenantDataSourceInitializationException.class.getName());
        });
    }

    @Test
    void skipsABrokenTenantWhenFailFastIsOff() {
        runner.withPropertyValues(
                "multitenancy.fail-fast=false",
                "multitenancy.datasources.ke.driver-class-name=com.example.MissingDriver").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(TenantDataSourceProvider.class).getTenantIds()).containsExactly("ug");
        });
    }

    @Test
    void backsOffForACustomConfigProvider() {
        TenantDataSourceConfig ng = TestTenants.h2("ng");
        runner.withBean(TenantDataSourceConfigProvider.class, () -> new TenantDataSourceConfigProvider() {
            @Override
            public Map<String, TenantDataSourceConfig> getTenantDataSourceConfigs() {
                return Map.of("ng", ng);
            }

            @Override
            public TenantDataSourceConfig getTenantDataSourceConfig(String tenantId) {
                return "ng".equals(tenantId) ? ng : null;
            }
        }).run(context -> assertThat(context.getBean(TenantDataSourceProvider.class).getTenantIds()).containsExactly("ng"));
    }

    @Test
    void propagatesTheTenantToTheApplicationTaskExecutor() {
        runner.run(context -> {
            TaskExecutor executor = context.getBean("applicationTaskExecutor", TaskExecutor.class);
            CompletableFuture<String> tenant = new CompletableFuture<>();

            TenantContext.runAs("ke", () -> executor.execute(() -> tenant.complete(TenantContext.current().orElse("none"))));

            assertThat(tenant.get()).isEqualTo("ke");
        });
    }

    @Test
    void replacesBootsDbHealthIndicatorWithAPerTenantCheck() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(TenantDataSourcesHealthIndicator.class);
            assertThat(context.getBean("dbHealthIndicator")).isInstanceOf(TenantDataSourcesHealthIndicator.class);
            Health health = context.getBean(TenantDataSourcesHealthIndicator.class).health(true);
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("tenants", Map.of("ug", "UP", "ke", "UP"));
        });
    }

    @Test
    void healthIsDownWhenATenantDatabaseIsUnreachable() {
        runner.withPropertyValues(
                "multitenancy.datasources.ke.url=jdbc:h2:tcp://localhost:1/missing",
                "multitenancy.datasources.ke.initialization-fail-timeout=-1",
                "multitenancy.datasources.ke.connection-timeout=250").run(context -> {
            Health health = context.getBean(TenantDataSourcesHealthIndicator.class).health(true);
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails().get("tenants")).asString().contains("ug=UP").contains("ke={");
        });
    }

    @Test
    void doesNotRegisterTheTenantFilterOutsideAServletApplication() {
        runner.run(context -> assertThat(context).doesNotHaveBean(FilterRegistrationBean.class));
    }

    @Test
    void worksWithoutSpringMvcOnTheClasspath() {
        runner.withClassLoader(new FilteredClassLoader("org.springframework.web", "jakarta.servlet"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TenantRoutingDataSource.class);
                });
    }

    @Test
    void registersTheTenantFilterInAServletApplication() {
        new WebApplicationContextRunner()
                .withConfiguration(AUTO_CONFIGURATIONS)
                .withPropertyValues("multitenancy.datasources.ug.url=" + TestTenants.h2Url("ug"),
                        "multitenancy.resolution.filter-order=-500")
                .run(context -> {
                    FilterRegistrationBean<?> filter = context.getBean("tenantResolutionFilter", FilterRegistrationBean.class);
                    assertThat(filter.getOrder()).isEqualTo(-500);
                });
    }

    @Test
    void tenantFilterCanBeSwitchedOff() {
        new WebApplicationContextRunner()
                .withConfiguration(AUTO_CONFIGURATIONS)
                .withPropertyValues("multitenancy.datasources.ug.url=" + TestTenants.h2Url("ug"),
                        "multitenancy.resolution.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean("tenantResolutionFilter"));
    }
}
