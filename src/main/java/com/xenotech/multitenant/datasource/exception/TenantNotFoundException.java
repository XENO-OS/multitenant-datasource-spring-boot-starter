package com.xenotech.multitenant.datasource.exception;

public class TenantNotFoundException extends IllegalStateException {

    public TenantNotFoundException() {
        super("No tenant found in the current context. Please set a tenant ID before accessing tenant-specific resources");
    }

    public TenantNotFoundException(String message) {
        super(message);
    }

}
