package com.kisf.sqlquery.core.cache;

import com.kisf.sqlquery.core.entity.DatasourceConfig;
import com.kisf.sqlquery.core.repo.DatasourceConfigRepository;
import com.kisf.sqlquery.core.util.AesUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DataSourceCache {

    private final DatasourceConfigRepository repo;
    private final Map<String, HikariDataSource> dataSourceMap = new ConcurrentHashMap<>();
    private final Map<String, SqlSessionFactory> factoryMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DataSourceCache(DatasourceConfigRepository repo) {
        this.repo = repo;
    }

    public SqlSessionFactory getOrCreate(String datasourceId) {
        return factoryMap.computeIfAbsent(datasourceId, id -> {
            DatasourceConfig config = repo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Datasource not found: " + id));
            if (!config.getEnabled()) {
                throw new IllegalArgumentException("Datasource is disabled: " + id);
            }
            DataSource ds = createDataSource(config);
            return createSqlSessionFactory(ds);
        });
    }

    public void invalidate(String datasourceId) {
        SqlSessionFactory oldFactory = factoryMap.remove(datasourceId);
        HikariDataSource oldDs = dataSourceMap.remove(datasourceId);
        if (oldDs != null && !oldDs.isClosed()) {
            oldDs.close();
        }
    }

    public void invalidateAll() {
        for (String id : factoryMap.keySet()) {
            invalidate(id);
        }
    }

    private HikariDataSource createDataSource(DatasourceConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName(config.getDriverClass());
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(AesUtils.decrypt(config.getPassword()));
        hikariConfig.setMaximumPoolSize(config.getPoolSize());

        if (config.getExtra() != null && !config.getExtra().isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> extra = objectMapper.readValue(config.getExtra(), Map.class);
                if (extra.containsKey("connectionTimeout")) {
                    hikariConfig.setConnectionTimeout(((Number) extra.get("connectionTimeout")).longValue());
                }
                if (extra.containsKey("maxLifetime")) {
                    hikariConfig.setMaxLifetime(((Number) extra.get("maxLifetime")).longValue());
                }
                if (extra.containsKey("idleTimeout")) {
                    hikariConfig.setIdleTimeout(((Number) extra.get("idleTimeout")).longValue());
                }
            } catch (Exception ignored) {
            }
        }

        HikariDataSource ds = new HikariDataSource(hikariConfig);
        dataSourceMap.put(config.getId(), ds);
        return ds;
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
