package com.kisf.sqlquery.core.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DmlSafetyValidatorTest {

    private DmlSafetyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DmlSafetyValidator();
    }

    @Test
    void shouldAllowDeleteWithStaticWhere() {
        assertThatCode(() -> validator.validate(
                "<delete>DELETE FROM t WHERE id=#{id}</delete>"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectDeleteWithoutWhere() {
        assertThatThrownBy(() -> validator.validate(
                "<delete>DELETE FROM t</delete>"))
                .isInstanceOf(DmlSafetyException.class)
                .hasMessageContaining("WHERE");
    }

    @Test
    void shouldAllowDeleteWithDynamicWhere() {
        assertThatCode(() -> validator.validate(
                "<delete>DELETE FROM t <where><if test=\"id!=null\">id=#{id}</if></where></delete>"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowUpdateWithStaticWhere() {
        assertThatCode(() -> validator.validate(
                "<update>UPDATE t SET name=#{n} WHERE id=#{id}</update>"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectUpdateWithoutWhere() {
        assertThatThrownBy(() -> validator.validate(
                "<update>UPDATE t SET name=#{n}</update>"))
                .isInstanceOf(DmlSafetyException.class)
                .hasMessageContaining("WHERE");
    }

    @Test
    void shouldAllowInsert() {
        assertThatCode(() -> validator.validate(
                "<insert>INSERT INTO t VALUES (#{v})</insert>"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowSelect() {
        assertThatCode(() -> validator.validate(
                "<select>SELECT * FROM t</select>"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectRenderedUpdateWithoutWhere() {
        assertThatThrownBy(() -> validator.validateRenderedSql(
                "UPDATE t SET name = ?"))
                .isInstanceOf(DmlSafetyException.class)
                .hasMessageContaining("执行时");
    }

    @Test
    void shouldIgnoreWhereInsideCommentsAndQuotedValues() {
        assertThatThrownBy(() -> validator.validateRenderedSql(
                "DELETE FROM t /* WHERE id = ? */ -- where name = ?\n"))
                .isInstanceOf(DmlSafetyException.class);
        assertThatThrownBy(() -> validator.validateRenderedSql(
                "UPDATE t SET note = 'where is it'"))
                .isInstanceOf(DmlSafetyException.class);
    }

    @Test
    void shouldAllowRenderedUpdateWithWhere() {
        assertThatCode(() -> validator.validateRenderedSql(
                "UPDATE t SET name = ? WHERE id = ?"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectDynamicWhereWhenNoConditionIsRendered() {
        MyBatisScriptEngine engine = new MyBatisScriptEngine();
        String template = "<update>UPDATE t SET name=#{name} "
                + "<where><if test=\"id != null\">id=#{id}</if></where></update>";
        String renderedSql = engine.parse("/dynamic-update", template,
                        new org.apache.ibatis.session.Configuration())
                .getBoundSql(java.util.Collections.singletonMap("name", "test"))
                .getSql();

        assertThatThrownBy(() -> validator.validateRenderedSql(renderedSql))
                .isInstanceOf(DmlSafetyException.class);
    }

    @Test
    void shouldRejectDollarSubstitutionByDefault() {
        assertThatThrownBy(() -> validator.validate(
                "<select>SELECT * FROM t ORDER BY ${column}</select>"))
                .isInstanceOf(DmlSafetyException.class)
                .hasMessageContaining("禁止使用 ${}");
    }

    @Test
    void shouldAllowDollarSubstitutionWhenExplicitlyEnabled() {
        DmlSafetyValidator permissiveValidator = new DmlSafetyValidator(true);

        assertThatCode(() -> permissiveValidator.validate(
                "<select>SELECT * FROM t ORDER BY ${column}</select>"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectMultipleRenderedStatements() {
        assertThatThrownBy(() -> validator.validateRenderedSql(
                org.apache.ibatis.mapping.SqlCommandType.SELECT, "SELECT 1; DELETE FROM t"))
                .isInstanceOf(DmlSafetyException.class)
                .hasMessageContaining("多条语句");
    }

    @Test
    void shouldRejectStatementTypeMismatch() {
        assertThatThrownBy(() -> validator.validateRenderedSql(
                org.apache.ibatis.mapping.SqlCommandType.UPDATE, "CREATE TABLE t(id INT)"))
                .isInstanceOf(DmlSafetyException.class)
                .hasMessageContaining("类型不一致");
    }
}
