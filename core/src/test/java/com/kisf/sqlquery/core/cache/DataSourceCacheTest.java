package com.kisf.sqlquery.core.cache;

import com.kisf.sqlquery.core.entity.DatasourceConfig;
import com.kisf.sqlquery.core.repo.DatasourceConfigRepository;
import com.kisf.sqlquery.core.util.AesUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DataSourceCacheTest {

    private DatasourceConfigRepository repo;
    private DataSourceCache cache;

    @BeforeEach
    void setUp() {
        repo = mock(DatasourceConfigRepository.class);
        cache = new DataSourceCache(repo);
    }

    @AfterEach
    void tearDown() {
        cache.invalidateAll();
    }

    @Test
    void shouldThrowWhenDatasourceNotFound() {
        when(repo.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cache.getOrCreate("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Datasource not found");
    }

    @Test
    void shouldThrowWhenDatasourceDisabled() {
        DatasourceConfig config = new DatasourceConfig();
        config.setId("test_db");
        config.setEnabled(false);
        when(repo.findById("test_db")).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> cache.getOrCreate("test_db"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void shouldCacheAndReturnSameFactory() {
        DatasourceConfig config = enabledDatasource("h2_db", null);
        when(repo.findById("h2_db")).thenReturn(Optional.of(config));

        SqlSessionFactory f1 = cache.getOrCreate("h2_db");
        SqlSessionFactory f2 = cache.getOrCreate("h2_db");

        assertThat(f1).isSameAs(f2);
        verify(repo, times(1)).findById("h2_db");
    }

    @Test
    void shouldRebuildFactoryAfterInvalidation() {
        DatasourceConfig config = enabledDatasource("h2_db", null);
        when(repo.findById("h2_db")).thenReturn(Optional.of(config));

        SqlSessionFactory first = cache.getOrCreate("h2_db");
        cache.invalidate("h2_db");
        SqlSessionFactory second = cache.getOrCreate("h2_db");

        assertThat(second).isNotSameAs(first);
        verify(repo, times(2)).findById("h2_db");
    }

    @Test
    void shouldRejectInvalidExtraConfiguration() {
        DatasourceConfig config = enabledDatasource("h2_db", "{invalid-json}");
        when(repo.findById("h2_db")).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> cache.getOrCreate("h2_db"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid extra configuration");
    }

    @Test
    void shouldReportCachedDatasourceHealthAndPoolStatistics() {
        DatasourceConfig config = enabledDatasource("health_db", null);
        when(repo.findById("health_db")).thenReturn(Optional.of(config));

        DataSourceHealth health = cache.checkHealth("health_db");

        assertThat(health.isHealthy()).isTrue();
        assertThat(health.getDatasourceId()).isEqualTo("health_db");
        assertThat(health.getTotalConnections()).isNotNull().isGreaterThanOrEqualTo(1);
        assertThat(health.getAwaitingConnections()).isZero();
    }

    @Test
    void shouldTestUnpersistedDatasourceWithPlaintextPassword() {
        DatasourceConfig config = enabledDatasource("preview_db", null);
        config.setPassword("");

        DataSourceHealth health = cache.testConnection(config);

        assertThat(health.isHealthy()).isTrue();
        assertThat(health.getMessage()).isEqualTo("ok");
        verify(repo, never()).findById(anyString());
    }

    private DatasourceConfig enabledDatasource(String id, String extra) {
        DatasourceConfig config = new DatasourceConfig();
        config.setId(id);
        config.setDriverClass("org.h2.Driver");
        config.setJdbcUrl("jdbc:h2:mem:" + id + ";DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword(AesUtils.encrypt(""));
        config.setPoolSize(5);
        config.setExtra(extra);
        config.setEnabled(true);
        return config;
    }
}
