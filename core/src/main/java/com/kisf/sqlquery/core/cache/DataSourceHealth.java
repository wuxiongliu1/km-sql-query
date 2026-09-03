package com.kisf.sqlquery.core.cache;

public class DataSourceHealth {

    private final String datasourceId;
    private final boolean healthy;
    private final String message;
    private final long elapsed;
    private final Integer activeConnections;
    private final Integer idleConnections;
    private final Integer totalConnections;
    private final Integer awaitingConnections;

    public DataSourceHealth(String datasourceId, boolean healthy, String message, long elapsed,
                            Integer activeConnections, Integer idleConnections,
                            Integer totalConnections, Integer awaitingConnections) {
        this.datasourceId = datasourceId;
        this.healthy = healthy;
        this.message = message;
        this.elapsed = elapsed;
        this.activeConnections = activeConnections;
        this.idleConnections = idleConnections;
        this.totalConnections = totalConnections;
        this.awaitingConnections = awaitingConnections;
    }

    public String getDatasourceId() { return datasourceId; }
    public boolean isHealthy() { return healthy; }
    public String getMessage() { return message; }
    public long getElapsed() { return elapsed; }
    public Integer getActiveConnections() { return activeConnections; }
    public Integer getIdleConnections() { return idleConnections; }
    public Integer getTotalConnections() { return totalConnections; }
    public Integer getAwaitingConnections() { return awaitingConnections; }
}
