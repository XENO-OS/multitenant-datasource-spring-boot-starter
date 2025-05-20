package com.xenotech.multitenant.datasource;

import com.xenotech.multitenant.datasource.config.TenantRoutingDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@TestConfiguration
public class TestDataSourceConfig {
    @Bean(name = "tenant1DataSource")
    public DataSource tenant1DataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:tenant1;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        return ds;
    }

    @Bean(name = "tenant2DataSource")
    public DataSource tenant2DataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:h2:mem:tenant2;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        return ds;
    }

    @Bean(name = "routingDataSource")
    public TenantRoutingDataSource routingDataSource(
            @Qualifier("tenant1DataSource") DataSource tenant1,
            @Qualifier("tenant2DataSource") DataSource tenant2) {

        Map<Object, Object> dataSources = new HashMap<>();
        dataSources.put("tenant1", tenant1);
        dataSources.put("tenant2", tenant2);

        TenantRoutingDataSource routing = new TenantRoutingDataSource();
        routing.setTargetDataSources(dataSources);
        routing.afterPropertiesSet();
        return routing;
    }

}
