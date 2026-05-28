package io.github.manormachine2207.hrsuite.tenant;

import java.util.UUID;

public final class TenantExceptions {

    private TenantExceptions() {
    }

    /** 409 — a tenant with the same unique attribute already exists. */
    public static class TenantConflictException extends RuntimeException {
        public TenantConflictException(String attribute, String value) {
            super("Tenant with " + attribute + " '" + value + "' already exists");
        }
    }

    /** 404 — no tenant for the given id. */
    public static class TenantNotFoundException extends RuntimeException {
        public TenantNotFoundException(UUID id) {
            super("Tenant " + id + " not found");
        }
    }
}
