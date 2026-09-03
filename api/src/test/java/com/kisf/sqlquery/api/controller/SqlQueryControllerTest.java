package com.kisf.sqlquery.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kisf.sqlquery.core.entity.DatasourceConfig;
import com.kisf.sqlquery.core.entity.SqlConfig;
import com.kisf.sqlquery.core.repo.DatasourceConfigRepository;
import com.kisf.sqlquery.core.repo.SqlConfigRepository;
import com.kisf.sqlquery.core.util.AesUtils;
import com.kisf.sqlquery.core.cache.DataSourceCache;
import com.kisf.sqlquery.core.engine.MyBatisScriptEngine;
import com.kisf.sqlquery.core.engine.SqlExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testint;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "server.servlet.multipart.max-request-size=10MB"
})
class SqlQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SqlConfigRepository sqlConfigRepo;

    @Autowired
    private DatasourceConfigRepository dsConfigRepo;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        sqlConfigRepo.deleteAll();
        dsConfigRepo.deleteAll();

        DatasourceConfig ds = new DatasourceConfig();
        ds.setId("h2_test");
        ds.setDriverClass("org.h2.Driver");
        ds.setJdbcUrl("jdbc:h2:mem:testexec;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword(AesUtils.encrypt(""));
        ds.setPoolSize(5);
        ds.setEnabled(true);
        dsConfigRepo.save(ds);

        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:testexec;DB_CLOSE_DELAY=-1", "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS orders "
                    + "(id INT PRIMARY KEY, name VARCHAR(100), amount DECIMAL(10,2))");
            statement.execute("DELETE FROM orders");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize test database", e);
        }
    }

    @Test
    void shouldExecuteSelectQuery() throws Exception {
        SqlConfig insert = new SqlConfig();
        insert.setSqlPath("/test/insert");
        insert.setSqlTemplate("<insert id=\"ins\">INSERT INTO orders(id, name, amount) VALUES(1, 'Test', 99.9)</insert>");
        insert.setDatasourceId("h2_test");
        insert.setEnabled(true);
        sqlConfigRepo.save(insert);

        SqlConfig select = new SqlConfig();
        select.setSqlPath("/test/select");
        select.setSqlTemplate("<select id=\"sel\">SELECT * FROM orders WHERE id = #{id}</select>");
        select.setDatasourceId("h2_test");
        select.setEnabled(true);
        sqlConfigRepo.save(select);

        // Insert
        mockMvc.perform(post("/api/sqlQuery")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(buildRequest("/test/insert", Collections.emptyMap()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // Select
        Map<String, Object> params = new HashMap<>();
        params.put("id", 1);
        mockMvc.perform(post("/api/sqlQuery")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(buildRequest("/test/select", params))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].NAME").value("Test"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.elapsed").isNumber());
    }

    @Test
    void shouldReturnErrorForUnknownSqlPath() throws Exception {
        mockMvc.perform(post("/api/sqlQuery")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(buildRequest("/nonexistent", Collections.emptyMap()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-2));
    }

    @Test
    void shouldExecuteSelectWithDynamicSql() throws Exception {
        SqlConfig select = new SqlConfig();
        select.setSqlPath("/test/dynamic");
        select.setSqlTemplate(
                "<select id=\"dyn\">SELECT * FROM orders " +
                "<where><if test=\"name != null\">AND name = #{name}</if></where></select>");
        select.setDatasourceId("h2_test");
        select.setEnabled(true);
        sqlConfigRepo.save(select);

        Map<String, Object> params = new HashMap<>();
        params.put("name", "Test");
        mockMvc.perform(post("/api/sqlQuery")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(buildRequest("/test/dynamic", params))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void shouldRefreshSqlPath() throws Exception {
        SqlConfig config = new SqlConfig();
        config.setSqlPath("/test/refreshme");
        config.setSqlTemplate("<select id=\"r\">SELECT 1 AS val</select>");
        config.setDatasourceId("h2_test");
        config.setEnabled(true);
        sqlConfigRepo.save(config);

        mockMvc.perform(post("/api/refresh/test/refreshme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.sqlPath").value("/test/refreshme"));
    }

    @Test
    void shouldReturnPaginationMetadataAndRespectRequestedPageSize() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:testexec;DB_CLOSE_DELAY=-1", "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO orders(id, name, amount) VALUES "
                    + "(1, 'A', 1), (2, 'B', 2), (3, 'C', 3)");
        }
        SqlConfig select = new SqlConfig();
        select.setSqlPath("/test/page");
        select.setSqlTemplate("<select id=\"page\">SELECT * FROM orders ORDER BY id</select>");
        select.setDatasourceId("h2_test");
        select.setEnabled(true);
        sqlConfigRepo.save(select);

        Map<String, Object> request = buildRequest("/test/page", Collections.emptyMap());
        request.put("page", 0);
        request.put("size", 2);
        mockMvc.perform(post("/api/sqlQuery")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    private Map<String, Object> buildRequest(String sqlPath, Map<String, Object> params) {
        Map<String, Object> request = new HashMap<>();
        request.put("sqlPath", sqlPath);
        request.put("params", params);
        return request;
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        public MyBatisScriptEngine myBatisScriptEngine() {
            return new MyBatisScriptEngine();
        }

        @Bean
        public DataSourceCache dataSourceCache(DatasourceConfigRepository repo) {
            return new DataSourceCache(repo);
        }

        @Bean
        public SqlExecutor sqlExecutor(SqlConfigRepository configRepo, DataSourceCache dataSourceCache,
                                       MyBatisScriptEngine scriptEngine) {
            return new SqlExecutor(configRepo, dataSourceCache, scriptEngine);
        }
    }
}
