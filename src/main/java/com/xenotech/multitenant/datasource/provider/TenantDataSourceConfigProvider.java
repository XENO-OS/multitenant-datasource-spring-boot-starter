package com.xenotech.multitenant.datasource.provider;

import java.util.Map;

/**
 * Interface for providing tenant datasource configurations from various sources.
 * Implementations can retrieve configurations from properties files, databases, etc.
 */
public interface TenantDataSourceConfigProvider {
    
    /**
     * Get all tenant configurations.
     * 
     * @return Map of tenant IDs to their datasource configurations
     */
    Map<String, TenantDataSourceConfig> getTenantDataSourceConfigs();
    
    /**
     * Get configuration for a specific tenant.
     * 
     * @param tenantId The tenant identifier
     * @return The datasource configuration for the tenant
     */
    TenantDataSourceConfig getTenantDataSourceConfig(String tenantId);
    
    /**
     * Class representing the configuration needed to create a datasource for a tenant.
     */
    class TenantDataSourceConfig {
        private String url;
        private String username;
        private String password;
        private String driverClassName;
        private int maximumPoolSize = 30;
        private int minimumIdle = 5;
        private long connectionTimeout = 3000;
        private long idleTimeout = 300000;
        private long maxLifetime = 1800000;
        private String poolName = "HikariConnPool";
        
        // Getters and setters
        public String getUrl() {
            return url;
        }
        
        public void setUrl(String url) {
            this.url = url;
        }
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getPassword() {
            return password;
        }
        
        public void setPassword(String password) {
            this.password = password;
        }
        
        public String getDriverClassName() {
            return driverClassName;
        }
        
        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }
        
        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }
        
        public void setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }
        
        public int getMinimumIdle() {
            return minimumIdle;
        }
        
        public void setMinimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
        }
        
        public long getConnectionTimeout() {
            return connectionTimeout;
        }
        
        public void setConnectionTimeout(long connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }
        
        public long getIdleTimeout() {
            return idleTimeout;
        }
        
        public void setIdleTimeout(long idleTimeout) {
            this.idleTimeout = idleTimeout;
        }
        
        public long getMaxLifetime() {
            return maxLifetime;
        }
        
        public void setMaxLifetime(long maxLifetime) {
            this.maxLifetime = maxLifetime;
        }
        
        public String getPoolName() {
            return poolName;
        }
        
        public void setPoolName(String poolName) {
            this.poolName = poolName;
        }
    }
}