package com.kisf.sqlquery.starter;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;
import com.kisf.sqlquery.core.repo.DatasourceConfigRepository;
import com.kisf.sqlquery.core.repo.SqlConfigRepository;
import com.kisf.sqlquery.admin.service.DatasourceConfigService;
import com.kisf.sqlquery.admin.service.SqlConfigService;
import com.kisf.sqlquery.admin.service.impl.DatasourceConfigServiceImpl;
import com.kisf.sqlquery.admin.service.impl.SqlConfigServiceImpl;
import com.kisf.sqlquery.core.engine.DmlSafetyValidator;
import com.kisf.sqlquery.core.cache.DataSourceCache;
import com.kisf.sqlquery.core.engine.MyBatisScriptEngine;
import com.kisf.sqlquery.core.engine.SqlExecutor;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import io.micrometer.core.instrument.MeterRegistry;
import com.kisf.sqlquery.core.engine.SqlQueryMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan(basePackages = {
        "com.kisf.sqlquery.admin.controller",
        "com.kisf.sqlquery.api.controller",
        "com.kisf.sqlquery.api.exception"
})
@EntityScan("com.kisf.sqlquery.core.entity")
@EnableJpaRepositories("com.kisf.sqlquery.core.repo")
public class SqlQueryAutoConfiguration {

    @Bean
    public Module hibernate5Module() {
        Hibernate5Module module = new Hibernate5Module();
        module.disable(Hibernate5Module.Feature.USE_TRANSIENT_ANNOTATION);
        return module;
    }

    @Bean
    public MyBatisScriptEngine myBatisScriptEngine() {
        return new MyBatisScriptEngine();
    }

    @Bean
    public DatasourceConfigService datasourceConfigService(DatasourceConfigRepository repo,
                                                             SqlConfigRepository sqlConfigRepository,
                                                             DataSourceCache dataSourceCache) {
        return new DatasourceConfigServiceImpl(repo, dataSourceCache::invalidate,
                sqlConfigRepository::existsByDatasourceId);
    }

    @Bean
    public DmlSafetyValidator dmlSafetyValidator(
            @Value("${km.sql-query.allow-dollar-substitution:false}") boolean allowDollarSubstitution) {
        return new DmlSafetyValidator(allowDollarSubstitution);
    }

    @Bean
    public SqlConfigService sqlConfigService(SqlConfigRepository repo, MyBatisScriptEngine scriptEngine,
                                              DmlSafetyValidator dmlSafetyValidator) {
        return new SqlConfigServiceImpl(repo, scriptEngine::invalidate, dmlSafetyValidator);
    }

    @Bean
    public DataSourceCache dataSourceCache(DatasourceConfigRepository repo) {
        return new DataSourceCache(repo);
    }

    @Bean
    public SqlExecutor sqlExecutor(SqlConfigRepository configRepo, DataSourceCache dataSourceCache,
                                   MyBatisScriptEngine scriptEngine, DmlSafetyValidator dmlSafetyValidator,
                                   @Value("${km.sql-query.timeout-seconds:30}") int queryTimeoutSeconds,
                                   @Value("${km.sql-query.max-rows:1000}") int maxRows,
                                   @Value("${km.sql-query.slow-sql-threshold-ms:1000}") long slowSqlThreshold,
                                   ObjectProvider<MeterRegistry> meterRegistryProvider) {
        SqlQueryMetrics metrics = new SqlQueryMetrics(
                meterRegistryProvider.getIfAvailable(), slowSqlThreshold);
        return new SqlExecutor(configRepo, dataSourceCache, scriptEngine,
                dmlSafetyValidator, queryTimeoutSeconds, maxRows, metrics);
    }
}
