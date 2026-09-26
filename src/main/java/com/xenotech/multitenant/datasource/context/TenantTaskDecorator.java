package com.xenotech.multitenant.datasource.context;

import org.springframework.core.task.TaskDecorator;

/**
 * Propagates the submitting thread's tenant to tasks run by Spring executors ({@code @Async},
 * {@code applicationTaskExecutor}, virtual-thread executors). Spring Boot applies every
 * {@link TaskDecorator} bean to the executors it builds, so registering this bean is enough.
 */
public class TenantTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        return TenantContext.wrap(runnable);
    }
}
