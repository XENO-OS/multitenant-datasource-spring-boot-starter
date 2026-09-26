package com.xenotech.multitenant.datasource.context;

import com.xenotech.multitenant.datasource.exception.TenantNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void normalisesTenantsToTrimmedLowerCase() {
        TenantContext.setCurrentTenant(" UG ");

        assertThat(TenantContext.current()).contains("ug");
        assertThat(TenantContext.normalize("   ")).isNull();
        assertThat(TenantContext.normalize(null)).isNull();
    }

    @Test
    void currentIsEmptyAndRequiredThrowsWhenNoTenantIsBound() {
        assertThat(TenantContext.current()).isEmpty();
        assertThatThrownBy(TenantContext::required).isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void clearUnbindsTheTenant() {
        TenantContext.setCurrentTenant("ug");
        TenantContext.clear();

        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void runAsBindsTheTenantAndAlwaysClearsIt() {
        TenantContext.runAs("KE", () -> assertThat(TenantContext.required()).isEqualTo("ke"));
        assertThat(TenantContext.current()).isEmpty();

        assertThatThrownBy(() -> TenantContext.runAs("ke", (Runnable) () -> {
            throw new IllegalArgumentException("boom");
        })).isInstanceOf(IllegalArgumentException.class);
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void runAsReturnsTheSupplierResultAndRestoresAnOuterTenant() {
        TenantContext.setCurrentTenant("ug");

        String inner = TenantContext.runAs("ng", TenantContext::required);

        assertThat(inner).isEqualTo("ng");
        assertThat(TenantContext.current()).contains("ug");
    }

    @Test
    void wrappedTasksRunUnderTheSubmittingThreadsTenant() throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            TenantContext.setCurrentTenant("ke");

            String viaSupplier = CompletableFuture.supplyAsync(TenantContext.wrap(TenantContext::required), executor).get();
            String viaExecutor = CompletableFuture.supplyAsync(TenantContext::required, TenantContext.propagating(executor)).get();
            String afterwards = executor.submit(() -> TenantContext.current().orElse("none")).get();

            assertThat(viaSupplier).isEqualTo("ke");
            assertThat(viaExecutor).isEqualTo("ke");
            assertThat(afterwards).isEqualTo("none");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void taskDecoratorPropagatesTheTenant() throws Exception {
        TenantContext.setCurrentTenant("ug");
        Runnable decorated = new TenantTaskDecorator().decorate(() -> assertThat(TenantContext.required()).isEqualTo("ug"));
        TenantContext.clear();

        Thread thread = Thread.ofVirtual().start(decorated);
        thread.join();

        assertThat(TenantContext.current()).isEmpty();
    }
}
