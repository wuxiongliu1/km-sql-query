package com.kisf.sqlquery.core.engine;

import com.kisf.sqlquery.core.entity.SqlConfig;
import com.kisf.sqlquery.core.repo.SqlConfigRepository;
import com.kisf.sqlquery.core.cache.DataSourceCache;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.RowBounds;
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
    void shouldRejectBlankSqlPathBeforeRepositoryLookup() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> executor.execute("  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sqlPath must not be blank");

        verifyNoInteractions(configRepo);
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

    @Test
    void shouldCommitUpdateExecution() {
        SqlConfig config = sqlConfig("/update", "<update>UPDATE t SET name=#{name} WHERE id=#{id}</update>");
        SqlSessionFactory factory = mock(SqlSessionFactory.class);
        SqlSession session = mock(SqlSession.class);
        when(configRepo.findBySqlPath("/update")).thenReturn(Optional.of(config));
        when(dataSourceCache.getOrCreate("test_db")).thenReturn(factory);
        when(factory.getConfiguration()).thenReturn(new Configuration());
        when(factory.openSession()).thenReturn(session);
        when(session.update(eq("/update"), anyMap())).thenReturn(1);

        SqlExecutor.ExecuteResult result = executor.execute("/update", java.util.Collections.emptyMap());

        assertThat(result.getData()).isEqualTo(1);
        verify(session).commit();
        verify(session, never()).rollback();
        assertThat(factory.getConfiguration().getMappedStatement("/update").getTimeout()).isEqualTo(30);
    }

    @Test
    void shouldReuseRegisteredStatementWhenTemplateIsUnchanged() {
        SqlConfig config = sqlConfig("/select", "<select>SELECT 1</select>");
        SqlSessionFactory factory = mock(SqlSessionFactory.class);
        SqlSession firstSession = mock(SqlSession.class);
        SqlSession secondSession = mock(SqlSession.class);
        Configuration configuration = new Configuration();
        when(configRepo.findBySqlPath("/select")).thenReturn(Optional.of(config));
        when(dataSourceCache.getOrCreate("test_db")).thenReturn(factory);
        when(factory.getConfiguration()).thenReturn(configuration);
        when(factory.openSession()).thenReturn(firstSession, secondSession);
        when(firstSession.selectList(eq("/select"), anyMap(), any(RowBounds.class)))
                .thenReturn(java.util.Collections.emptyList());
        when(secondSession.selectList(eq("/select"), anyMap(), any(RowBounds.class)))
                .thenReturn(java.util.Collections.emptyList());

        executor.execute("/select", java.util.Collections.emptyMap());
        Object firstStatement = configuration.getMappedStatement("/select");
        executor.execute("/select", java.util.Collections.emptyMap());

        assertThat(configuration.getMappedStatement("/select")).isSameAs(firstStatement);
    }

    @Test
    void shouldLimitSelectResultsAndReportMoreRows() {
        executor = new SqlExecutor(configRepo, dataSourceCache, scriptEngine,
                new DmlSafetyValidator(), 30, 2);
        SqlConfig config = sqlConfig("/select", "<select>SELECT id FROM t</select>");
        SqlSessionFactory factory = mock(SqlSessionFactory.class);
        SqlSession session = mock(SqlSession.class);
        when(configRepo.findBySqlPath("/select")).thenReturn(Optional.of(config));
        when(dataSourceCache.getOrCreate("test_db")).thenReturn(factory);
        when(factory.getConfiguration()).thenReturn(new Configuration());
        when(factory.openSession()).thenReturn(session);
        when(session.selectList(eq("/select"), anyMap(), any(RowBounds.class)))
                .thenReturn(java.util.Arrays.asList(1, 2, 3));

        SqlExecutor.ExecuteResult result = executor.execute("/select", null);

        assertThat((java.util.List<?>) result.getData()).containsExactly(1, 2);
        assertThat(result.isHasMore()).isTrue();
        assertThat(result.isTruncated()).isTrue();
        assertThat(result.getSize()).isEqualTo(2);
    }

    @Test
    void shouldRollbackWhenWriteExecutionFails() {
        SqlConfig config = sqlConfig("/delete", "<delete>DELETE FROM t WHERE id=#{id}</delete>");
        SqlSessionFactory factory = mock(SqlSessionFactory.class);
        SqlSession session = mock(SqlSession.class);
        when(configRepo.findBySqlPath("/delete")).thenReturn(Optional.of(config));
        when(dataSourceCache.getOrCreate("test_db")).thenReturn(factory);
        when(factory.getConfiguration()).thenReturn(new Configuration());
        when(factory.openSession()).thenReturn(session);
        when(session.delete(eq("/delete"), anyMap())).thenThrow(new RuntimeException("write failed"));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> executor.execute("/delete", java.util.Collections.emptyMap()))
                .hasMessage("write failed");

        verify(session).rollback();
        verify(session, never()).commit();
    }

    @Test
    void shouldPreserveExecutionFailureWhenRollbackAlsoFails() {
        SqlConfig config = sqlConfig("/delete", "<delete>DELETE FROM t WHERE id=#{id}</delete>");
        SqlSessionFactory factory = mock(SqlSessionFactory.class);
        SqlSession session = mock(SqlSession.class);
        RuntimeException executionFailure = new RuntimeException("write failed");
        RuntimeException rollbackFailure = new RuntimeException("rollback failed");
        when(configRepo.findBySqlPath("/delete")).thenReturn(Optional.of(config));
        when(dataSourceCache.getOrCreate("test_db")).thenReturn(factory);
        when(factory.getConfiguration()).thenReturn(new Configuration());
        when(factory.openSession()).thenReturn(session);
        when(session.delete(eq("/delete"), anyMap())).thenThrow(executionFailure);
        doThrow(rollbackFailure).when(session).rollback();

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> executor.execute("/delete", null));

        assertThat(thrown).isSameAs(executionFailure);
        assertThat(thrown.getSuppressed()).containsExactly(rollbackFailure);
    }

    @Test
    void shouldAlwaysRollbackTemplateTestExecution() {
        SqlSessionFactory factory = mock(SqlSessionFactory.class);
        SqlSession session = mock(SqlSession.class);
        when(dataSourceCache.getOrCreate("test_db")).thenReturn(factory);
        when(factory.getConfiguration()).thenReturn(new Configuration());
        when(factory.openSession()).thenReturn(session);
        when(session.update(anyString(), anyMap())).thenReturn(3);

        SqlExecutor.ExecuteResult result = executor.testExecute(
                "test_db", "<update>UPDATE t SET name=#{name} WHERE id=#{id}</update>",
                java.util.Collections.emptyMap(), null, null);

        assertThat(result.getData()).isEqualTo(3);
        verify(session).rollback();
        verify(session, never()).commit();
    }

    private SqlConfig sqlConfig(String sqlPath, String template) {
        SqlConfig config = new SqlConfig();
        config.setSqlPath(sqlPath);
        config.setSqlTemplate(template);
        config.setDatasourceId("test_db");
        config.setEnabled(true);
        return config;
    }
}
