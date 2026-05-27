package com.kisf.sqlquery.core.engine;

public class DmlSafetyValidator {

    public void validate(String sqlTemplate) {
        String trimmed = sqlTemplate.trim().toLowerCase();
        if (trimmed.startsWith("<select") || trimmed.startsWith("<insert")) {
            return;
        }
        if (!trimmed.startsWith("<delete") && !trimmed.startsWith("<update")) {
            throw new SqlExecutor.ScriptParseException(
                    "SQL模板必须以 <select>, <insert>, <update> 或 <delete> 开头");
        }

        String innerSql = MyBatisScriptEngine.extractInnerSql(sqlTemplate);
        if (!innerSql.toLowerCase().contains("where")) {
            throw new DmlSafetyException(
                    "DELETE/UPDATE 操作必须包含 WHERE 条件，不允许全表操作");
        }
    }
}
