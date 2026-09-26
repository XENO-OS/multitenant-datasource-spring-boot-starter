package com.xenotech.multitenant.datasource;

import com.xenotech.multitenant.datasource.provider.TenantDataSourceConfigProvider.TenantDataSourceConfig;

import java.util.UUID;

public final class TestTenants {
    private TestTenants() {
    }

    /**
     * @return a config for a fresh, private in-memory H2 database
     */
    public static TenantDataSourceConfig h2(String name) {
        TenantDataSourceConfig config = new TenantDataSourceConfig();
        config.setUrl(h2Url(name));
        config.setUsername("sa");
        config.setPassword("");
        return config;
    }

    public static String h2Url(String name) {
        return "jdbc:h2:mem:" + name + "-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=MySQL";
    }
}
