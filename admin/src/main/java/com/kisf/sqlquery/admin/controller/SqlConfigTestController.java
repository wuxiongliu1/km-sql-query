package com.kisf.sqlquery.admin.controller;

import com.kisf.sqlquery.admin.model.SqlConfigTestRequest;
import com.kisf.sqlquery.core.engine.SqlExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sqlConfig")
public class SqlConfigTestController {

    private final SqlExecutor sqlExecutor;

    public SqlConfigTestController(SqlExecutor sqlExecutor) {
        this.sqlExecutor = sqlExecutor;
    }

    @PostMapping("/test")
    public ResponseEntity<SqlExecutor.ExecuteResult> test(
            @RequestBody SqlConfigTestRequest request) {
        return ResponseEntity.ok(sqlExecutor.testExecute(
                request.getDatasourceId(), request.getSqlTemplate(), request.getParams(),
                request.getPage(), request.getSize()));
    }
}
