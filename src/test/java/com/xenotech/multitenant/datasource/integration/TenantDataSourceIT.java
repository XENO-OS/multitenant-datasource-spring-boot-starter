package com.xenotech.multitenant.datasource.integration;

import com.xenotech.multitenant.datasource.TestApplication;
import com.xenotech.multitenant.datasource.TestDataSourceConfig;
import com.xenotech.multitenant.datasource.config.TenantRoutingDataSource;
import com.xenotech.multitenant.datasource.context.TenantContext;
import com.xenotech.multitenant.datasource.exception.TenantNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestDataSourceConfig.class)
public class TenantDataSourceIT {
    @Autowired
    @Qualifier("routingDataSource")
    private TenantRoutingDataSource routingDataSource;

    @Autowired
    @Qualifier("tenant1DataSource")
    private DataSource tenant1DataSource;

    @Autowired
    @Qualifier("tenant2DataSource")
    private DataSource tenant2DataSource;

    @BeforeEach
    void setUp() {
        // Clear any existing tenant context
        TenantContext.clear();

        // Setup our test data sources
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("tenant1", tenant1DataSource);
        targetDataSources.put("tenant2", tenant2DataSource);

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.afterPropertiesSet();
    }
    @AfterEach
    void tearDown() {
        TenantContext.clear();
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
