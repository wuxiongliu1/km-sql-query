package com.kisf.sqlquery.core.engine;

import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.scripting.xmltags.DynamicSqlSource;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MyBatisScriptEngineTest {

    private MyBatisScriptEngine engine;
    private Configuration configuration;

    @BeforeEach
    void setUp() {
        engine = new MyBatisScriptEngine();
        configuration = new Configuration();
        configuration.addLoadedResource("test");
    }

    @Test
    void shouldDetectSelectCommand() {
        assertThat(engine.detectCommandType("<select id=\"q\">SELECT 1</select>"))
                .isEqualTo(SqlCommandType.SELECT);
    }

    @Test
    void shouldDetectInsertCommand() {
        assertThat(engine.detectCommandType("<insert id=\"q\">INSERT INTO t VALUES(1)</insert>"))
                .isEqualTo(SqlCommandType.INSERT);
    }

    @Test
    void shouldDetectUpdateCommand() {
        assertThat(engine.detectCommandType("<update id=\"q\">UPDATE t SET x=1</update>"))
                .isEqualTo(SqlCommandType.UPDATE);
    }

    @Test
    void shouldDetectDeleteCommand() {
        assertThat(engine.detectCommandType("<delete id=\"q\">DELETE FROM t</delete>"))
                .isEqualTo(SqlCommandType.DELETE);
    }

    @Test
    void shouldThrowOnInvalidTemplate() {
        assertThatThrownBy(() -> engine.detectCommandType("SELECT * FROM t"))
                .isInstanceOf(SqlExecutor.ScriptParseException.class)
                .hasMessageContaining("SQL模板必须以");
    }

    @Test
    void shouldParseSelectTemplate() {
        String template = "<select id=\"q\">SELECT * FROM orders WHERE id = #{orderId}</select>";
        assertThat(engine.parse("/test", template, configuration)).isNotNull();
    }

    @Test
    void shouldParseTemplateWithIfTag() {
        String template = "<select id=\"q\">SELECT * FROM orders " +
                "<where><if test=\"status != null\">AND status = #{status}</if></where></select>";
        assertThat(engine.parse("/test2", template, configuration))
                .isInstanceOf(DynamicSqlSource.class);
    }

    @Test
    void shouldParseTemplateWithForEach() {
        String template = "<select id=\"q\">SELECT * FROM orders WHERE id IN " +
                "<foreach collection=\"ids\" item=\"id\" open=\"(\" separator=\",\" close=\")\">#{id}</foreach></select>";
        assertThat(engine.parse("/test3", template, configuration)).isNotNull();
    }

    @Test
    void shouldCacheSqlSource() {
        String template = "<select id=\"q\">SELECT 1</select>";
        Object s1 = engine.parse("/cached", template, configuration);
        Object s2 = engine.parse("/cached", template, configuration);
        assertThat(s1).isSameAs(s2);
    }

    @Test
    void shouldInvalidateFromCache() {
        String template = "<select id=\"q\">SELECT 1</select>";
        engine.parse("/inval", template, configuration);
        engine.invalidate("/inval");
        // re-parse should create new SqlSource
        String template2 = "<select id=\"q\">SELECT 2</select>";
        Object s = engine.parse("/inval", template2, configuration);
        assertThat(s).isNotNull();
    }

    @Test
    void shouldRejectDocumentTypeDeclarations() {
        String template = "<!DOCTYPE select [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<select>&xxe;</select>";

        assertThatThrownBy(() -> engine.parse("/xxe", template, configuration))
                .isInstanceOf(SqlExecutor.ScriptParseException.class)
                .hasMessageContaining("Failed to parse SQL template");
    }
}
