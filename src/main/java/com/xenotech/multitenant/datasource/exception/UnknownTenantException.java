package com.xenotech.multitenant.datasource.exception;

import lombok.Getter;

/**
 * A tenant was supplied but no datasource is configured for it.
 */
@Getter
public class UnknownTenantException extends TenantResolutionException {
    private final String tenant;

    public UnknownTenantException(String tenant) {
        super("Unknown tenant: " + tenant);
        this.tenant = tenant;
    }
}
