package com.kisf.sqlquery.api.model;

import java.util.List;
import java.util.Map;

public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;
    private Integer total;
    private Long elapsed;

    public static ApiResponse<List<Map<String, Object>>> okList(List<Map<String, Object>> data, long elapsed) {
        ApiResponse<List<Map<String, Object>>> resp = new ApiResponse<>();
        resp.code = 0;
        resp.message = "ok";
        resp.data = data;
        resp.total = data.size();
        resp.elapsed = elapsed;
        return resp;
    }

    public static ApiResponse<Map<String, Object>> okUpdate(Map<String, Object> data, long elapsed) {
        ApiResponse<Map<String, Object>> resp = new ApiResponse<>();
        resp.code = 0;
        resp.message = "ok";
        resp.data = data;
        resp.elapsed = elapsed;
        return resp;
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.code = code;
        resp.message = message;
        return resp;
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public Integer getTotal() { return total; }
    public void setTotal(Integer total) { this.total = total; }

    public Long getElapsed() { return elapsed; }
    public void setElapsed(Long elapsed) { this.elapsed = elapsed; }
}
