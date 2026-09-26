package com.xenotech.multitenant.datasource.exception;

/**
 * Base class for every failure to establish which tenant a unit of work belongs to.
 * Catch this to handle a missing and an unknown tenant the same way.
 */
public class TenantResolutionException extends IllegalStateException {
    public TenantResolutionException() {
        super("Error while trying to resolve the current tenant");
    }

    public TenantResolutionException(String message) {
        super(message);
    }
}
