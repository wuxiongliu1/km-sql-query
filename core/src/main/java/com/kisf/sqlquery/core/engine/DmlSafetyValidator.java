package com.kisf.sqlquery.core.engine;

import java.util.Locale;
import java.util.regex.Pattern;

public class DmlSafetyValidator {

    private static final Pattern WHERE_TOKEN = Pattern.compile("\\bwhere\\b", Pattern.CASE_INSENSITIVE);
    private final boolean allowDollarSubstitution;

    public DmlSafetyValidator() {
        this(false);
    }

    public DmlSafetyValidator(boolean allowDollarSubstitution) {
        this.allowDollarSubstitution = allowDollarSubstitution;
    }

    public void validate(String sqlTemplate) {
        if (!allowDollarSubstitution && sqlTemplate.contains("${")) {
            throw new DmlSafetyException("SQL模板禁止使用 ${} 直接字符串替换，请使用 #{} 参数绑定");
        }
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

    /**
     * Validates the SQL produced after MyBatis has evaluated all dynamic tags.
     * This closes the gap where a template contains a {@code <where>} element,
     * but all nested conditions evaluate to false at runtime.
     */
    public void validateRenderedSql(String sql) {
        validateRenderedSql(null, sql);
    }

    public void validateRenderedSql(org.apache.ibatis.mapping.SqlCommandType declaredType, String sql) {
        String searchableSql = stripCommentsAndQuotedContent(sql);
        String firstKeyword = firstKeyword(searchableSql);
        if (firstKeyword.isEmpty()) {
            throw new DmlSafetyException("MyBatis 未生成可执行 SQL");
        }
        if (containsMultipleStatements(searchableSql)) {
            throw new DmlSafetyException("不允许在一个 SQL 模板中执行多条语句");
        }
        if (declaredType != null && !declaredType.name().equalsIgnoreCase(firstKeyword)) {
            throw new DmlSafetyException(
                    "SQL模板标签与实际语句类型不一致: " + declaredType + " != "
                            + firstKeyword.toUpperCase(Locale.ENGLISH));
        }
        if (("update".equals(firstKeyword) || "delete".equals(firstKeyword))
                && !WHERE_TOKEN.matcher(searchableSql).find()) {
            throw new DmlSafetyException(
                    "DELETE/UPDATE 操作执行时必须生成 WHERE 条件，不允许全表操作");
        }
    }

    private boolean containsMultipleStatements(String sql) {
        int separator = sql.indexOf(';');
        if (separator < 0) {
            return false;
        }
        return !sql.substring(separator + 1).trim().isEmpty();
    }

    private String firstKeyword(String sql) {
        String trimmed = sql.trim().toLowerCase(Locale.ENGLISH);
        int end = 0;
        while (end < trimmed.length() && Character.isLetter(trimmed.charAt(end))) {
            end++;
        }
        return trimmed.substring(0, end);
    }

    private String stripCommentsAndQuotedContent(String sql) {
        StringBuilder result = new StringBuilder(sql.length());
        boolean lineComment = false;
        boolean blockComment = false;
        char quote = 0;

        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : 0;

            if (lineComment) {
                if (current == '\n' || current == '\r') {
                    lineComment = false;
                    result.append(' ');
                }
                continue;
            }
            if (blockComment) {
                if (current == '*' && next == '/') {
                    blockComment = false;
                    i++;
                    result.append(' ');
                }
                continue;
            }
            if (quote != 0) {
                if (current == quote) {
                    if (next == quote) {
                        i++;
                    } else {
                        quote = 0;
                    }
                }
                continue;
            }
            if (current == '-' && next == '-') {
                lineComment = true;
                i++;
            } else if (current == '/' && next == '*') {
                blockComment = true;
                i++;
            } else if (current == '\'' || current == '"' || current == '`') {
                quote = current;
                result.append(' ');
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }
}
