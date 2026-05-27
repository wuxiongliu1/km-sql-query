package com.kisf.sqlquery.api.model;

import java.util.Map;

public class SqlQueryRequest {

    private String sqlPath;
    private Map<String, Object> params;

    public String getSqlPath() { return sqlPath; }
    public void setSqlPath(String sqlPath) { this.sqlPath = sqlPath; }

    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
}
