package com.xenotech.multitenant.datasource.examples;

import com.xenotech.multitenant.datasource.provider.TenantDataSourceConfigProvider;
import com.xenotech.multitenant.datasource.provider.impl.DatabaseTenantDataSourceConfigProvider;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Example configuration class showing how to use the database implementation
 * of TenantDataSourceConfigProvider.
 * 
 * This is not used by default but serves as an example for custom implementations.
 */
@Configuration
public class DatabaseTenantConfigExample {
    
    /**
     * Create a custom TenantDataSourceConfigProvider bean that reads from a database.
     * This will replace the default PropertiesTenantDataSourceConfigProvider.
     */
    @Bean
    public TenantDataSourceConfigProvider tenantDataSourceConfigProvider(DataSource masterDataSource) {
        return new DatabaseTenantDataSourceConfigProvider(masterDataSource);
    }
    
    /**
     * Create a master datasource to connect to the database that stores tenant configurations.
     * This datasource is used by the DatabaseTenantDataSourceConfigProvider to read tenant configurations.
     */
    @Bean
    public DataSource masterDataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:postgresql://localhost:5432/master");
        dataSource.setUsername("master_user");
        dataSource.setPassword("master_pass");
        dataSource.setDriverClassName("org.postgresql.Driver");
        return dataSource;
    }
}