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
}
