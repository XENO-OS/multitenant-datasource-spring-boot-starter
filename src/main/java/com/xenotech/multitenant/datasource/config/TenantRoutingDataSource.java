package com.xenotech.multitenant.datasource.config;

import com.xenotech.multitenant.datasource.context.TenantContext;
import com.xenotech.multitenant.datasource.exception.TenantNotFoundException;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TenantRoutingDataSource extends AbstractRoutingDataSource to provide dynamic
 * routing of data sources based on the current tenant context.
 * This class determines which data source should be used based on the tenant identifier
 * stored in the TenantContext.
 */
public class TenantRoutingDataSource extends AbstractRoutingDataSource {
    private final Map<Object, Object> targetDataSources = new ConcurrentHashMap<>();

    @Override
    public void afterPropertiesSet() {
        // Set the resolved target data sources for the AbstractRoutingDataSource
        super.setTargetDataSources(this.targetDataSources);
        super.afterPropertiesSet();
    }

    /**
     * Initialize the data sources with a map of tenant data sources.
     *
     * @param dataSources the initial map of tenant data sources
     */
    @Override
    public void setTargetDataSources(Map<Object, Object> dataSources) {
        this.targetDataSources.putAll(dataSources);
        super.setTargetDataSources(this.targetDataSources);
        this.afterPropertiesSet();
    }

    /**
     * Determines the current tenant identifier to route the data source.
     * This method is called by AbstractRoutingDataSource to decide which
     * data source should be used for the current request.
     *
     * @return The current tenant identifier, which is used to select the appropriate
     * data source from the map of target data sources.
     */
    @Override
    protected Object determineCurrentLookupKey() {
        // Retrieve the current tenant identifier from the TenantContext
        // This identifier is used to determine which data source to use
        return TenantContext.getCurrentTenant();
    }
    /**
     * Adds a new tenant data source to the routing configuration.
     *
     * This method:
     * 1. Stores the data source in the target data sources map with the tenant ID as key
     * 2. Updates the underlying AbstractRoutingDataSource with the complete set of target data sources
     * 3. Reinitializes the data source router to make the new data source immediately available
     *
     * Note: This operation is thread-safe as it uses a ConcurrentHashMap internally.
     *
     * @param tenantId The unique identifier for the tenant (case-sensitive)
     * @param dataSource The configured DataSource instance for this tenant
     * @throws IllegalArgumentException if either parameter is null
     */
    public void addDataSource(String tenantId, DataSource dataSource) {
        this.targetDataSources.put(tenantId, dataSource);
        super.setTargetDataSources(this.targetDataSources);
        super.afterPropertiesSet();
    }
    /**
     * Removes a tenant data source from the routing configuration.
     *
     * This method:
     * 1. Removes the data source associated with the tenant ID from the target data sources map
     * 2. Updates the underlying AbstractRoutingDataSource with the modified set of target data sources
     * 3. Reinitializes the data source router to reflect the removal
     *
     * Note: If the specified tenant ID doesn't exist, the operation will complete silently.
     *
     * @param tenantId The unique identifier for the tenant to remove (case-sensitive)
     * @throws IllegalArgumentException if tenantId is null
     */
    public void removeDataSource(String tenantId) {
        this.targetDataSources.remove(tenantId);
        super.setTargetDataSources(this.targetDataSources);
        super.afterPropertiesSet();
    }
    /**
     * Determines the actual target DataSource based on the current lookup key.
     * Overrides the parent method to provide custom exception handling.
     *
     * @return the target DataSource
     * @throws TenantNotFoundException if no DataSource is found for the current tenant
     */
    @Override
    protected DataSource determineTargetDataSource() {
        Object lookupKey = determineCurrentLookupKey();
        DataSource dataSource = (DataSource) this.targetDataSources.get(lookupKey);

        if (dataSource == null) {
            throw new TenantNotFoundException(
                    "No datasource configured for tenant: " + lookupKey);
        }
        return dataSource;
    }
}
