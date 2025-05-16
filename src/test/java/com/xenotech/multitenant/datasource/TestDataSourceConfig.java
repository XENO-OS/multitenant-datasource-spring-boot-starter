package com.xenotech.multitenant.datasource;

import com.xenotech.multitenant.datasource.autoconfigure.TenantProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@TestConfiguration
public class TestDataSourceConfig {
    @Bean
    public DataSource tenant1DataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:tenant1;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
//        ds.setDriverClassName("org.h2.Driver");
        return ds;
    }

    @Bean
    public DataSource tenant2DataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:tenant2;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
//        ds.setDriverClassName("org.h2.Driver");
        return ds;
    }
//    @Bean
//    public TenantProperties tenantConfigProperties() {
//        TenantProperties properties = new TenantProperties();
//        Map<String, TenantProperties.TenantConfig> tenants = new HashMap<>();
//
//        TenantProperties.TenantConfig tenant1 = new TenantProperties.TenantConfig();
//        tenant1.setUrl("jdbc:h2:mem:tenant1;DB_CLOSE_DELAY=-1;MODE=MySQL");
//        tenant1.setUsername("sa");
//        tenant1.setPassword("");
//
//        TenantProperties.TenantConfig tenant2 = new TenantProperties.TenantConfig();
//        tenant2.setUrl("jdbc:h2:mem:tenant2;DB_CLOSE_DELAY=-1;MODE=MySQL");
//        tenant2.setUsername("sa");
//        tenant2.setPassword("");
//
//        tenants.put("tenant1", tenant1);
//        tenants.put("tenant2", tenant2);
//
//        properties.setDatasources(tenants);
//        return properties;
//    }
}
