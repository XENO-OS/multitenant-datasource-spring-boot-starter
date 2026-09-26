package com.xenotech.multitenant.datasource.provider;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HikariCP settings shared by {@code multitenancy.datasource-defaults} and each tenant.
 *
 * <p>Every value is optional. A tenant's value wins over the default, and an unset default falls back to
 * HikariCP's own default. Durations accept a unit ({@code 30s}, {@code 5m}) or plain milliseconds.
 */
@Getter
@Setter
public class PoolSettings {
    /**
     * JDBC driver class name. When unset, the driver is inferred from the JDBC URL.
     */
    private String driverClassName;

    /**
     * Maximum number of connections in the pool.
     */
    private Integer maximumPoolSize;

    /**
     * Minimum number of idle connections kept in the pool.
     */
    private Integer minimumIdle;

    /**
     * Maximum time to wait for a connection from the pool.
     */
    private Duration connectionTimeout;

    /**
     * Maximum time a connection may sit idle before it is retired.
     */
    private Duration idleTimeout;

    /**
     * Maximum lifetime of a connection.
     */
    private Duration maxLifetime;

    /**
     * Time a connection may be out of the pool before a possible leak is logged. 0 disables leak detection.
     */
    private Duration leakDetectionThreshold;

    /**
     * How often idle connections are pinged to keep them alive. 0 disables keepalive.
     */
    private Duration keepaliveTime;

    /**
     * Maximum time to wait for a connection to be validated.
     */
    private Duration validationTimeout;

    /**
     * How long the pool tries to open its first connection at startup. A positive value fails startup
     * when the database is unreachable, 0 validates without waiting, and a negative value skips the check.
     */
    private Duration initializationFailTimeout;

    /**
     * Driver properties passed to the pool, e.g. {@code connectionAttributes: program_name:my-service}
     * or {@code connectionTimeZone: Africa/Kampala}. A tenant's entries are merged over the defaults.
     */
    private Map<String, String> dataSourceProperties = new LinkedHashMap<>();
}
