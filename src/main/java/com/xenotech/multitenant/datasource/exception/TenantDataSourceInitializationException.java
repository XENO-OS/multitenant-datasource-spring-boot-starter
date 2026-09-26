package com.xenotech.multitenant.datasource.exception;

import lombok.Getter;

/**
 * A tenant's connection pool could not be built. Thrown at startup when {@code multitenancy.fail-fast} is on.
 */
@Getter
public class TenantDataSourceInitializationException extends IllegalStateException {
    private final String tenant;

    public TenantDataSourceInitializationException(String tenant, Throwable cause) {
        super("Failed to initialise the datasource for tenant '" + tenant + "': " + cause.getMessage(), cause);
        this.tenant = tenant;
    }
}
