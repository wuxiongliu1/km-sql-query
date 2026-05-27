package com.kisf.sqlquery.api.controller;

import com.kisf.sqlquery.core.engine.SqlExecutor;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/refresh")
public class RefreshController {

    private final SqlExecutor sqlExecutor;

    public RefreshController(SqlExecutor sqlExecutor) {
        this.sqlExecutor = sqlExecutor;
    }

    @PostMapping("/**")
    public Map<String, Object> refresh(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String prefix = "/api/refresh/";
        String sqlPath = uri.substring(uri.indexOf(prefix) + prefix.length());
        sqlExecutor.invalidate(sqlPath);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("sqlPath", sqlPath);
        return result;
    }
}
