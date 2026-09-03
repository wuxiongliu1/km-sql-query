package com.kisf.sqlquery.admin.service.impl;

import com.kisf.sqlquery.core.engine.DmlSafetyValidator;
import com.kisf.sqlquery.core.entity.SqlConfig;
import com.kisf.sqlquery.core.repo.SqlConfigRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.Consumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlConfigServiceImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldInvalidateOnlyChangedSqlPath() {
        SqlConfigRepository repo = mock(SqlConfigRepository.class);
        Consumer<String> invalidator = mock(Consumer.class);
        SqlConfig config = sqlConfig("/orders/list");
        when(repo.save(config)).thenReturn(config);
        SqlConfigServiceImpl service = new SqlConfigServiceImpl(
                repo, invalidator, new DmlSafetyValidator());

        service.save(config);

        verify(invalidator).accept("/orders/list");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldInvalidateOldAndNewPathAfterRename() {
        SqlConfigRepository repo = mock(SqlConfigRepository.class);
        Consumer<String> invalidator = mock(Consumer.class);
        SqlConfig existing = sqlConfig("/orders/old");
        SqlConfig request = sqlConfig("/orders/new");
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);
        SqlConfigServiceImpl service = new SqlConfigServiceImpl(
                repo, invalidator, new DmlSafetyValidator());

        service.update(1L, request);

        verify(invalidator).accept("/orders/old");
        verify(invalidator).accept("/orders/new");
    }

    private SqlConfig sqlConfig(String sqlPath) {
        SqlConfig config = new SqlConfig();
        config.setSqlPath(sqlPath);
        config.setSqlTemplate("<select>SELECT 1</select>");
        config.setDatasourceId("main");
        config.setEnabled(true);
        return config;
    }
}
