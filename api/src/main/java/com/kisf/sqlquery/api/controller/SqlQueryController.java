package com.kisf.sqlquery.api.controller;

import com.kisf.sqlquery.api.model.ApiResponse;
import com.kisf.sqlquery.api.model.SqlQueryRequest;
import com.kisf.sqlquery.core.engine.SqlExecutor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
public class SqlQueryController {

    private final SqlExecutor sqlExecutor;

    public SqlQueryController(SqlExecutor sqlExecutor) {
        this.sqlExecutor = sqlExecutor;
    }

    @PostMapping("/api/sqlQuery")
    public ApiResponse<?> execute(@RequestBody SqlQueryRequest request) {
        SqlExecutor.ExecuteResult result = sqlExecutor.execute(
                request.getSqlPath(),
                request.getParams() != null ? request.getParams() : Collections.emptyMap());

        if (result.isList()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) result.getData();
            return ApiResponse.okList(rows, result.getElapsed());
        } else {
            Map<String, Object> updateResult = Collections.singletonMap("affectedRows", result.getData());
            return ApiResponse.okUpdate(updateResult, result.getElapsed());
        }
    }
}
