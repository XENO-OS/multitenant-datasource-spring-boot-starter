package com.xenotech.multitenant.datasource.integration;

import com.xenotech.multitenant.datasource.config.TenantRoutingDataSource;
import com.xenotech.multitenant.datasource.context.TenantContext;
import com.xenotech.multitenant.datasource.exception.TenantNotFoundException;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TenantDataSourceIT {
    private TenantRoutingDataSource routingDataSource;
    private DataSource tenant1DataSource;
    private DataSource tenant2DataSource;

    @BeforeEach
    void setUp() {
        // Clear any existing tenant context
        TenantContext.clear();

        // Create tenant1 data source
        HikariDataSource ds1 = new HikariDataSource();
        ds1.setJdbcUrl("jdbc:h2:mem:tenant1;DB_CLOSE_DELAY=-1;MODE=MySQL");
        ds1.setUsername("sa");
        ds1.setPassword("");
        tenant1DataSource = ds1;

        // Create tenant2 data source
        HikariDataSource ds2 = new HikariDataSource();
        ds2.setJdbcUrl("jdbc:h2:mem:tenant2;DB_CLOSE_DELAY=-1;MODE=MySQL");
        ds2.setUsername("sa");
        ds2.setPassword("");
        tenant2DataSource = ds2;

        // Setup our routing data source
        routingDataSource = new TenantRoutingDataSource();
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("tenant1", tenant1DataSource);
        targetDataSources.put("tenant2", tenant2DataSource);
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.afterPropertiesSet();
    }
    @AfterEach
    void tearDown() {
        TenantContext.clear();

        // Close the data sources
        if (tenant1DataSource instanceof HikariDataSource) {
            ((HikariDataSource) tenant1DataSource).close();
        }
        if (tenant2DataSource instanceof HikariDataSource) {
            ((HikariDataSource) tenant2DataSource).close();
        }
    }
    @Test
    void shouldRouteToCorrectDataSourceForTenant1() throws SQLException {
        // Arrange
        TenantContext.setCurrentTenant("tenant1");

        // Test through public getConnection() method
        try (Connection connection = routingDataSource.getConnection()) {
            assertNotNull(connection);

            // Verify it's from the right datasource
            try (Connection tenant1Connection = tenant1DataSource.getConnection()) {
                assertEquals(connection.getMetaData().getURL(),
                        tenant1Connection.getMetaData().getURL());
            }
        }
    }
    @Test
    void shouldRouteToCorrectDataSourceForTenant2() throws SQLException {
        // Arrange
        TenantContext.setCurrentTenant("tenant2");

        // Test through public getConnection() method
        try (Connection connection = routingDataSource.getConnection()) {
            assertNotNull(connection);

            // Verify it's from the right datasource
            try (Connection tenant2Connection = tenant2DataSource.getConnection()) {
                assertEquals(connection.getMetaData().getURL(),
                        tenant2Connection.getMetaData().getURL());
            }
        }
    }

    @Test
    void shouldThrowExceptionForUnknownTenant() {
        // Arrange
        TenantContext.setCurrentTenant("non-existent-tenant");

        // Act & Assert
        TenantNotFoundException exception = assertThrows(TenantNotFoundException.class, () -> {
            routingDataSource.getConnection();
        });

        assertEquals("No datasource configured for tenant: non-existent-tenant",
                exception.getMessage());
    }
    @Test
    void shouldThrowExceptionWhenNoTenantProvided() {
        // Arrange - ensure no tenant is set
        TenantContext.clear();

        // Act & Assert
        TenantNotFoundException exception = assertThrows(TenantNotFoundException.class, () -> {
            routingDataSource.getConnection();
        });

        // Verify the exception message
        assertEquals("No tenant found in the current context.", exception.getMessage());
    }
}
