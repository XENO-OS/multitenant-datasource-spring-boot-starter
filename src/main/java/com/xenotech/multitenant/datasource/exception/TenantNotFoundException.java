package com.xenotech.multitenant.datasource.exception;

/**
 * No tenant was supplied: the request carried no tenant header, or no tenant is bound to the current thread.
 */
public class TenantNotFoundException extends TenantResolutionException {
    public TenantNotFoundException() {
        super("No tenant found in the current context. Please set a tenant ID before accessing tenant-specific resources");
    }

    public TenantNotFoundException(String message) {
        super(message);
    }
}
