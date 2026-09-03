package com.kisf.sqlquery.api.model;

import java.util.List;
import java.util.Map;

public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;
    private Integer total;
    private Long elapsed;
    private Integer page;
    private Integer size;
    private Boolean hasMore;
    private Boolean truncated;

    public static ApiResponse<List<Map<String, Object>>> okList(List<Map<String, Object>> data, long elapsed) {
        return okList(data, elapsed, 0, data.size(), false, false);
    }

    public static ApiResponse<List<Map<String, Object>>> okList(
            List<Map<String, Object>> data, long elapsed, int page, int size,
            boolean hasMore, boolean truncated) {
        ApiResponse<List<Map<String, Object>>> resp = new ApiResponse<>();
        resp.code = 0;
        resp.message = "ok";
        resp.data = data;
        resp.total = data.size();
        resp.elapsed = elapsed;
        resp.page = page;
        resp.size = size;
        resp.hasMore = hasMore;
        resp.truncated = truncated;
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

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }

    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }

    public Boolean getHasMore() { return hasMore; }
    public void setHasMore(Boolean hasMore) { this.hasMore = hasMore; }

    public Boolean getTruncated() { return truncated; }
    public void setTruncated(Boolean truncated) { this.truncated = truncated; }
}
