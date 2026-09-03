package com.kisf.sqlquery.admin.controller;

import com.kisf.sqlquery.admin.model.SqlConfigTestRequest;
import com.kisf.sqlquery.core.engine.SqlExecutor;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlConfigTestControllerTest {

    @Test
    void shouldDelegateUnpublishedTemplateToRollbackExecutor() {
        SqlExecutor executor = mock(SqlExecutor.class);
        SqlExecutor.ExecuteResult expected = new SqlExecutor.ExecuteResult(
                Collections.singletonList(Collections.singletonMap("ID", 1)), 5,
                0, 10, false, false);
        when(executor.testExecute("main", "<select>SELECT 1</select>",
                Collections.emptyMap(), 0, 10)).thenReturn(expected);
        SqlConfigTestRequest request = new SqlConfigTestRequest();
        request.setDatasourceId("main");
        request.setSqlTemplate("<select>SELECT 1</select>");
        request.setParams(Collections.emptyMap());
        request.setPage(0);
        request.setSize(10);

        SqlConfigTestController controller = new SqlConfigTestController(executor);

        assertThat(controller.test(request).getBody()).isSameAs(expected);
        verify(executor).testExecute("main", "<select>SELECT 1</select>",
                Collections.emptyMap(), 0, 10);
    }
}
