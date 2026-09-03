package com.kisf.sqlquery.core.engine;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class SqlQueryMetrics {

    private static final Logger log = LoggerFactory.getLogger(SqlQueryMetrics.class);
    private static final SqlQueryMetrics NOOP = new SqlQueryMetrics(null, Long.MAX_VALUE);

    private final MeterRegistry registry;
    private final long slowSqlThresholdMillis;

    public SqlQueryMetrics(MeterRegistry registry, long slowSqlThresholdMillis) {
        this.registry = registry;
        this.slowSqlThresholdMillis = slowSqlThresholdMillis;
    }

    public static SqlQueryMetrics noop() {
        return NOOP;
    }

    public void record(String sqlPath, long elapsedMillis, boolean success, int returnedRows) {
        String normalizedPath = sqlPath == null || sqlPath.trim().isEmpty() ? "unknown" : sqlPath;
        String outcome = success ? "success" : "failure";
        if (registry != null) {
            Timer.builder("km.sql.query.duration")
                    .description("SQL interface execution duration")
                    .tags("sqlPath", normalizedPath, "outcome", outcome)
                    .register(registry)
                    .record(elapsedMillis, TimeUnit.MILLISECONDS);
            if (success) {
                DistributionSummary.builder("km.sql.query.returned.rows")
                        .description("Rows returned or affected by a SQL interface")
                        .tag("sqlPath", normalizedPath)
                        .register(registry)
                        .record(returnedRows);
            }
        }
        if (elapsedMillis >= slowSqlThresholdMillis) {
            log.warn("Slow SQL interface: sqlPath={}, elapsedMs={}, outcome={}, rows={}",
                    normalizedPath, elapsedMillis, outcome, returnedRows);
        }
    }
}
