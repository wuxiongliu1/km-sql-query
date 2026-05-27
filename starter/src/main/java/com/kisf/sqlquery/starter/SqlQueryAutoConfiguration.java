package com.kisf.sqlquery.starter;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;
import com.kisf.sqlquery.admin.repo.DatasourceConfigRepository;
import com.kisf.sqlquery.admin.repo.SqlConfigRepository;
import com.kisf.sqlquery.admin.service.DatasourceConfigService;
import com.kisf.sqlquery.admin.service.SqlConfigService;
import com.kisf.sqlquery.admin.service.impl.DatasourceConfigServiceImpl;
import com.kisf.sqlquery.admin.service.impl.SqlConfigServiceImpl;
import com.kisf.sqlquery.core.engine.DmlSafetyValidator;
import com.kisf.sqlquery.core.cache.DataSourceCache;
import com.kisf.sqlquery.core.engine.MyBatisScriptEngine;
import com.kisf.sqlquery.core.engine.SqlExecutor;
import org.springframework.boot.autoconfigure.domain.EntityScan;
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
@EntityScan("com.kisf.sqlquery.admin.entity")
@EnableJpaRepositories("com.kisf.sqlquery.admin.repo")
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
    public DatasourceConfigService datasourceConfigService(DatasourceConfigRepository repo) {
        return new DatasourceConfigServiceImpl(repo);
    }

    @Bean
    public DmlSafetyValidator dmlSafetyValidator() {
        return new DmlSafetyValidator();
    }

    @Bean
    public SqlConfigService sqlConfigService(SqlConfigRepository repo, MyBatisScriptEngine scriptEngine,
                                              DmlSafetyValidator dmlSafetyValidator) {
        return new SqlConfigServiceImpl(repo, scriptEngine::invalidateAll, dmlSafetyValidator);
    }

    @Bean
    public DataSourceCache dataSourceCache(DatasourceConfigRepository repo) {
        return new DataSourceCache(repo);
    }

    @Bean
    public SqlExecutor sqlExecutor(SqlConfigRepository configRepo, DataSourceCache dataSourceCache,
                                   MyBatisScriptEngine scriptEngine) {
        return new SqlExecutor(configRepo, dataSourceCache, scriptEngine);
    }
}
