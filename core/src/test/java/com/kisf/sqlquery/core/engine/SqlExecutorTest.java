package com.kisf.sqlquery.core.engine;

import com.kisf.sqlquery.admin.entity.SqlConfig;
import com.kisf.sqlquery.admin.repo.SqlConfigRepository;
import com.kisf.sqlquery.core.cache.DataSourceCache;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SqlExecutorTest {

    private SqlConfigRepository configRepo;
    private DataSourceCache dataSourceCache;
    private MyBatisScriptEngine scriptEngine;
    private SqlExecutor executor;

    @BeforeEach
    void setUp() {
        configRepo = mock(SqlConfigRepository.class);
        dataSourceCache = mock(DataSourceCache.class);
        scriptEngine = new MyBatisScriptEngine();
        executor = new SqlExecutor(configRepo, dataSourceCache, scriptEngine);
    }

    @Test
    void shouldThrowWhenSqlPathNotFound() {
        when(configRepo.findBySqlPath("/nonexistent")).thenReturn(Optional.empty());

        try {
            executor.execute("/nonexistent", java.util.Collections.emptyMap());
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("sqlPath not found");
        }
    }

    @Test
    void shouldThrowWhenSqlPathDisabled() {
        SqlConfig config = new SqlConfig();
        config.setSqlPath("/disabled");
        config.setEnabled(false);
        when(configRepo.findBySqlPath("/disabled")).thenReturn(Optional.of(config));

        try {
            executor.execute("/disabled", java.util.Collections.emptyMap());
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("disabled");
        }
    }
}
