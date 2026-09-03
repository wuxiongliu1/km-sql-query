package com.kisf.sqlquery.core.cache;

import com.kisf.sqlquery.core.entity.DatasourceConfig;
import com.kisf.sqlquery.core.repo.DatasourceConfigRepository;
import com.kisf.sqlquery.core.util.AesUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DataSourceCache {

    private static final int LOCK_STRIPES = 32;

    private final DatasourceConfigRepository repo;
    private final Map<String, HikariDataSource> dataSourceMap = new ConcurrentHashMap<>();
    private final Map<String, SqlSessionFactory> factoryMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Object[] creationLocks = new Object[LOCK_STRIPES];

    public DataSourceCache(DatasourceConfigRepository repo) {
        this.repo = repo;
        for (int i = 0; i < creationLocks.length; i++) {
            creationLocks[i] = new Object();
        }
    }

    public SqlSessionFactory getOrCreate(String datasourceId) {
        SqlSessionFactory cached = factoryMap.get(datasourceId);
        if (cached != null) {
            return cached;
        }

        synchronized (lockFor(datasourceId)) {
            cached = factoryMap.get(datasourceId);
            if (cached != null) {
                return cached;
            }

            DatasourceConfig config = repo.findById(datasourceId)
                    .orElseThrow(() -> new IllegalArgumentException("Datasource not found: " + datasourceId));
            if (!config.getEnabled()) {
                throw new IllegalArgumentException("Datasource is disabled: " + datasourceId);
            }

            HikariDataSource dataSource = createDataSource(config);
            try {
                SqlSessionFactory factory = createSqlSessionFactory(dataSource);
                dataSourceMap.put(datasourceId, dataSource);
                factoryMap.put(datasourceId, factory);
                return factory;
            } catch (RuntimeException e) {
                dataSource.close();
                throw e;
            }
        }
    }

    public void invalidate(String datasourceId) {
        synchronized (lockFor(datasourceId)) {
            factoryMap.remove(datasourceId);
            close(dataSourceMap.remove(datasourceId));
        }
    }

    public DataSourceHealth checkHealth(String datasourceId) {
        long start = System.currentTimeMillis();
        try {
            getOrCreate(datasourceId);
            HikariDataSource dataSource = dataSourceMap.get(datasourceId);
            try (Connection connection = dataSource.getConnection()) {
                if (!connection.isValid(5)) {
                    return unhealthy(datasourceId, "Connection validation failed", start);
                }
            }
            HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
            return new DataSourceHealth(datasourceId, true, "ok",
                    System.currentTimeMillis() - start, pool.getActiveConnections(),
                    pool.getIdleConnections(), pool.getTotalConnections(),
                    pool.getThreadsAwaitingConnection());
        } catch (Exception e) {
            return unhealthy(datasourceId, e.getMessage(), start);
        }
    }

    public DataSourceHealth testConnection(DatasourceConfig config) {
        long start = System.currentTimeMillis();
        HikariDataSource dataSource = null;
        try {
            HikariConfig hikariConfig = createHikariConfig(config, config.getPassword());
            hikariConfig.setMaximumPoolSize(1);
            hikariConfig.setMinimumIdle(0);
            dataSource = new HikariDataSource(hikariConfig);
            try (Connection connection = dataSource.getConnection()) {
                if (!connection.isValid(5)) {
                    return unhealthy(config.getId(), "Connection validation failed", start);
                }
            }
            return new DataSourceHealth(config.getId(), true, "ok",
                    System.currentTimeMillis() - start, 0, 1, 1, 0);
        } catch (Exception e) {
            return unhealthy(config.getId(), e.getMessage(), start);
        } finally {
            close(dataSource);
        }
    }

    @PreDestroy
    public void invalidateAll() {
        Set<String> datasourceIds = new HashSet<>(factoryMap.keySet());
        datasourceIds.addAll(dataSourceMap.keySet());
        for (String id : datasourceIds) {
            invalidate(id);
        }
    }

    private HikariDataSource createDataSource(DatasourceConfig config) {
        return new HikariDataSource(createHikariConfig(config, AesUtils.decrypt(config.getPassword())));
    }

    private HikariConfig createHikariConfig(DatasourceConfig config, String password) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName(config.getDriverClass());
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(config.getPoolSize());

        if (config.getExtra() != null && !config.getExtra().isEmpty()) {
            applyExtraConfiguration(hikariConfig, config.getExtra(), config.getId());
        }

        return hikariConfig;
    }

    private void applyExtraConfiguration(HikariConfig hikariConfig, String extraJson, String datasourceId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> extra = objectMapper.readValue(extraJson, Map.class);
            applyLong(extra, "connectionTimeout", hikariConfig::setConnectionTimeout);
            applyLong(extra, "maxLifetime", hikariConfig::setMaxLifetime);
            applyLong(extra, "idleTimeout", hikariConfig::setIdleTimeout);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid extra configuration for datasource: " + datasourceId, e);
        }
    }

    private void applyLong(Map<String, Object> extra, String name,
                           java.util.function.LongConsumer setter) {
        Object value = extra.get(name);
        if (value != null) {
            if (!(value instanceof Number)) {
                throw new IllegalArgumentException(name + " must be a number");
            }
            setter.accept(((Number) value).longValue());
        }
    }

    private Object lockFor(String datasourceId) {
        int index = (datasourceId.hashCode() & Integer.MAX_VALUE) % creationLocks.length;
        return creationLocks[index];
    }

    private void close(HikariDataSource dataSource) {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private DataSourceHealth unhealthy(String datasourceId, String message, long start) {
        return new DataSourceHealth(datasourceId, false,
                message == null ? "unknown error" : message,
                System.currentTimeMillis() - start, null, null, null, null);
    }

    private SqlSessionFactory createSqlSessionFactory(DataSource dataSource) {
        try {
            SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            return factoryBean.getObject();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SqlSessionFactory", e);
        }
    }
}
