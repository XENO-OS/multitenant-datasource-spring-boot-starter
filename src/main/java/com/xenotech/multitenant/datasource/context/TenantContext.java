package com.xenotech.multitenant.datasource.context;

import com.xenotech.multitenant.datasource.exception.TenantNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Holds the tenant for the current thread.
 *
 * <p>Tenant identifiers are case-insensitive: every value is trimmed and lower-cased on the way in,
 * so {@code UG} and {@code ug} select the same tenant.
 *
 * <p>Code that runs outside a request (schedulers, message listeners, health checks) should use
 * {@link #runAs(String, Runnable)} rather than {@link #setCurrentTenant(String)}, so the tenant is
 * always removed from the (possibly pooled) thread afterwards.
 */
@Slf4j
public final class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    /**
     * Normalises a tenant identifier: trimmed and lower-cased.
     *
     * @return the normalised identifier, or {@code null} if {@code tenant} is null or blank
     */
    public static String normalize(String tenant) {
        return StringUtils.hasText(tenant) ? tenant.trim().toLowerCase(Locale.ROOT) : null;
    }

    /**
     * @return the tenant bound to the current thread, if any
     */
    public static Optional<String> current() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    /**
     * @return the tenant bound to the current thread
     * @throws TenantNotFoundException if no tenant is bound
     */
    public static String required() {
        String tenant = CURRENT_TENANT.get();
        if (tenant == null) {
            throw new TenantNotFoundException();
        }
        return tenant;
    }

    /**
     * @return the tenant bound to the current thread, or {@code null}
     * @deprecated use {@link #current()} or {@link #required()}
     */
    @Deprecated(since = "3.0.0")
    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    /**
     * Binds a tenant to the current thread. Prefer {@link #runAs(String, Runnable)}, which also unbinds it.
     */
    public static void setCurrentTenant(String tenant) {
        Assert.hasText(tenant, "tenant cannot be empty");
        String normalized = normalize(tenant);
        log.trace("Setting current tenant to: {}", normalized);
        CURRENT_TENANT.set(normalized);
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }

    /**
     * Runs {@code task} with {@code tenant} bound, then restores whatever was bound before (nothing, usually).
     */
    public static void runAs(String tenant, Runnable task) {
        String previous = bind(tenant);
        try {
            task.run();
        } finally {
            restore(previous);
        }
    }

    /**
     * Returns the result of {@code task} with {@code tenant} bound, then restores whatever was bound before.
     */
    public static <T> T runAs(String tenant, Supplier<T> task) {
        String previous = bind(tenant);
        try {
            return task.get();
        } finally {
            restore(previous);
        }
    }

    /**
     * Like {@link #runAs(String, Supplier)} for work that throws checked exceptions.
     */
    public static <T> T callAs(String tenant, Callable<T> task) throws Exception {
        String previous = bind(tenant);
        try {
            return task.call();
        } finally {
            restore(previous);
        }
    }

    /**
     * Captures the current tenant so {@code task} runs under it on whichever thread executes it.
     * Returns {@code task} unchanged when no tenant is bound.
     */
    public static Runnable wrap(Runnable task) {
        String tenant = CURRENT_TENANT.get();
        return tenant == null ? task : () -> runAs(tenant, task);
    }

    /**
     * Captures the current tenant for a {@link Supplier}, e.g. for {@code CompletableFuture.supplyAsync}.
     */
    public static <T> Supplier<T> wrap(Supplier<T> task) {
        String tenant = CURRENT_TENANT.get();
        return tenant == null ? task : () -> runAs(tenant, task);
    }

    /**
     * Captures the current tenant for a {@link Callable}.
     */
    public static <T> Callable<T> wrapCallable(Callable<T> task) {
        String tenant = CURRENT_TENANT.get();
        return tenant == null ? task : () -> callAs(tenant, task);
    }

    /**
     * Returns an executor that runs each task under the tenant bound when the task was submitted,
     * e.g. {@code CompletableFuture.supplyAsync(work, TenantContext.propagating(executor))}.
     */
    public static Executor propagating(Executor executor) {
        return task -> executor.execute(wrap(task));
    }

    private static String bind(String tenant) {
        Assert.hasText(tenant, "tenant cannot be empty");
        String previous = CURRENT_TENANT.get();
        CURRENT_TENANT.set(normalize(tenant));
        return previous;
    }

    private static void restore(String previous) {
        if (previous == null) {
            CURRENT_TENANT.remove();
        } else {
            CURRENT_TENANT.set(previous);
        }
    }
}
