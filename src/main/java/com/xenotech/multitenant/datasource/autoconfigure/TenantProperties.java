package com.xenotech.multitenant.datasource.autoconfigure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "multitenancy")
public class TenantProperties {
    private Map<String, TenantConfig> datasources = new java.util.HashMap<>();

    @Getter
    @Setter
    public static class TenantConfig {
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
    }
}
