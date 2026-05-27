package com.kisf.sqlquery.api.exception;

import com.kisf.sqlquery.api.model.ApiResponse;
import com.kisf.sqlquery.core.engine.SqlExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "参数校验失败";
        int code = -1;
        if (msg.contains("not found")) {
            code = -2;
        } else if (msg.contains("Datasource")) {
            code = -3;
        } else if (msg.contains("disabled")) {
            code = -2;
        }
        return ApiResponse.error(code, msg);
    }

    @ExceptionHandler(SqlExecutor.ScriptParseException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleScriptParse(SqlExecutor.ScriptParseException e) {
        return ApiResponse.error(-5, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("Unexpected error", e);
        return ApiResponse.error(-4, "SQL执行异常: " + e.getMessage());
    }
}
