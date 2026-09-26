package com.xenotech.multitenant.datasource.provider.impl;

import com.xenotech.multitenant.datasource.provider.TenantDataSourceConfigProvider;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Example implementation that reads tenant configurations from a database.
 * This is not used by default but serves as an example for custom implementations.
 */
public class DatabaseTenantDataSourceConfigProvider implements TenantDataSourceConfigProvider {
    
    private final JdbcTemplate jdbcTemplate;
    
    public DatabaseTenantDataSourceConfigProvider(DataSource masterDataSource) {
        this.jdbcTemplate = new JdbcTemplate(masterDataSource);
    }
    
    @Override
    public Map<String, TenantDataSourceConfig> getTenantDataSourceConfigs() {
        Map<String, TenantDataSourceConfig> configs = new HashMap<>();
        
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT tenant_id, url, username, password, driver_class_name, " +
            "maximum_pool_size, minimum_idle, connection_timeout, " +
            "idle_timeout, max_lifetime, pool_name FROM tenant_datasources"
        );
        
        for (Map<String, Object> row : rows) {
            String tenantId = (String) row.get("tenant_id");
            TenantDataSourceConfig config = mapRowToConfig(row);
            configs.put(tenantId, config);
        }
        
        return configs;
    }
    
    @Override
    public TenantDataSourceConfig getTenantDataSourceConfig(String tenantId) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT url, username, password, driver_class_name, " +
                "maximum_pool_size, minimum_idle, connection_timeout, " +
                "idle_timeout, max_lifetime, pool_name FROM tenant_datasources " +
                "WHERE tenant_id = ?", tenantId
            );
            
            return mapRowToConfig(row);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
    
    private TenantDataSourceConfig mapRowToConfig(Map<String, Object> row) {
        TenantDataSourceConfig config = new TenantDataSourceConfig();
        config.setUrl((String) row.get("url"));
        config.setUsername((String) row.get("username"));
        config.setPassword((String) row.get("password"));
        config.setDriverClassName((String) row.get("driver_class_name"));
        config.setMaximumPoolSize((Integer) row.get("maximum_pool_size"));
        config.setMinimumIdle((Integer) row.get("minimum_idle"));
        config.setConnectionTimeout((Long) row.get("connection_timeout"));
        config.setIdleTimeout((Long) row.get("idle_timeout"));
        config.setMaxLifetime((Long) row.get("max_lifetime"));
        config.setPoolName((String) row.get("pool_name"));
        return config;
    }
}