package com.kisf.sqlquery.core.cache;

import com.kisf.sqlquery.admin.entity.DatasourceConfig;
import com.kisf.sqlquery.admin.repo.DatasourceConfigRepository;
import com.kisf.sqlquery.admin.util.AesUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
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
        DatasourceConfig config = new DatasourceConfig();
        config.setId("h2_db");
        config.setDriverClass("org.h2.Driver");
        config.setJdbcUrl("jdbc:h2:mem:test_cache;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword(AesUtils.encrypt(""));
        config.setPoolSize(5);
        config.setEnabled(true);
        when(repo.findById("h2_db")).thenReturn(Optional.of(config));

        SqlSessionFactory f1 = cache.getOrCreate("h2_db");
        SqlSessionFactory f2 = cache.getOrCreate("h2_db");

        assertThat(f1).isSameAs(f2);
    }
}
