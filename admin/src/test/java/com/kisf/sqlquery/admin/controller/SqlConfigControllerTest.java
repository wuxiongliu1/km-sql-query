package com.kisf.sqlquery.admin.controller;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;
import com.kisf.sqlquery.admin.entity.SqlConfig;
import com.kisf.sqlquery.admin.model.PagedResult;
import com.kisf.sqlquery.admin.repo.SqlConfigRepository;
import com.kisf.sqlquery.admin.service.SqlConfigService;
import com.kisf.sqlquery.admin.service.impl.SqlConfigServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testjson;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SqlConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SqlConfigRepository repo;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        repo.deleteAll();
    }

    @Test
    void shouldReturnNonNullContentInList() throws Exception {
        SqlConfig config = new SqlConfig();
        config.setSqlPath("/test/listSerialization");
        config.setSqlTemplate("<select id=\"s\">SELECT 1</select>");
        config.setDatasourceId("h2_test");
        config.setEnabled(true);
        repo.save(config);

        MvcResult result = mockMvc.perform(get("/api/admin/sqlConfig/list?page=0&size=10"))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();

        PagedResult<?> paged = objectMapper.readValue(json, PagedResult.class);
        assertThat(paged.getContent()).isNotNull();
        assertThat(paged.getContent()).isNotEmpty();
        assertThat(paged.getContent().get(0)).isNotNull();
        assertThat(paged.getTotalElements()).isEqualTo(1);
    }

    @Test
    void shouldSerializeAllFieldsCorrectly() throws Exception {
        SqlConfig config = new SqlConfig();
        config.setSqlPath("/test/fullSerialization");
        config.setSqlTemplate("<select id=\"s\">SELECT * FROM t</select>");
        config.setDatasourceId("mysql_main");
        config.setDescription("test description");
        config.setEnabled(true);
        repo.save(config);

        MvcResult result = mockMvc.perform(get("/api/admin/sqlConfig/list?page=0&size=10"))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();

        assertThat(json).contains("\"sqlPath\":\"/test/fullSerialization\"");
        assertThat(json).contains("\"sqlTemplate\":\"<select id=\\\"s\\\">SELECT * FROM t</select>\"");
        assertThat(json).contains("\"datasourceId\":\"mysql_main\"");
        assertThat(json).contains("\"description\":\"test description\"");
        assertThat(json).contains("\"enabled\":true");
        assertThat(json).doesNotContain("hibernateLazyInitializer");
        assertThat(json).doesNotContain("\"handler\"");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("com.kisf.sqlquery.admin.entity")
    @EnableJpaRepositories("com.kisf.sqlquery.admin.repo")
    static class TestConfig {

        @Bean
        public Module hibernate5Module() {
            Hibernate5Module module = new Hibernate5Module();
            module.disable(Hibernate5Module.Feature.USE_TRANSIENT_ANNOTATION);
            return module;
        }

        @Bean
        public SqlConfigService sqlConfigService(SqlConfigRepository repo) {
            return new SqlConfigServiceImpl(repo);
        }

        @Bean
        public SqlConfigController sqlConfigController(SqlConfigService sqlConfigService) {
            return new SqlConfigController(sqlConfigService);
        }
    }
}
