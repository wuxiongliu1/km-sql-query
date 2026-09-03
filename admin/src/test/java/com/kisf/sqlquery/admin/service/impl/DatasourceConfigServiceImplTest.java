package com.kisf.sqlquery.admin.service.impl;

import com.kisf.sqlquery.core.entity.DatasourceConfig;
import com.kisf.sqlquery.core.repo.DatasourceConfigRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatasourceConfigServiceImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldInvalidateCachedDatasourceAfterUpdate() {
        DatasourceConfigRepository repo = mock(DatasourceConfigRepository.class);
        Consumer<String> invalidator = mock(Consumer.class);
        DatasourceConfig existing = datasource("main", "old-url", "encrypted-password");
        DatasourceConfig request = datasource("ignored", "new-url", "");
        when(repo.findById("main")).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);
        DatasourceConfigServiceImpl service = new DatasourceConfigServiceImpl(repo, invalidator);

        DatasourceConfig updated = service.update("main", request);

        assertThat(updated.getJdbcUrl()).isEqualTo("new-url");
        assertThat(updated.getPassword()).isEqualTo("encrypted-password");
        verify(invalidator).accept("main");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRejectDeletingReferencedDatasource() {
        DatasourceConfigRepository repo = mock(DatasourceConfigRepository.class);
        Consumer<String> invalidator = mock(Consumer.class);
        java.util.function.Predicate<String> referenced = mock(java.util.function.Predicate.class);
        when(referenced.test("main")).thenReturn(true);
        DatasourceConfigServiceImpl service = new DatasourceConfigServiceImpl(
                repo, invalidator, referenced);

        assertThatThrownBy(() -> service.delete("main"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("referenced");

        verify(repo, never()).deleteById("main");
    }

    private DatasourceConfig datasource(String id, String jdbcUrl, String password) {
        DatasourceConfig config = new DatasourceConfig();
        config.setId(id);
        config.setDriverClass("org.h2.Driver");
        config.setJdbcUrl(jdbcUrl);
        config.setUsername("sa");
        config.setPassword(password);
        config.setPoolSize(5);
        config.setEnabled(true);
        return config;
    }
}
