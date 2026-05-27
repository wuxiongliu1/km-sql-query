package com.kisf.sqlquery.admin.repo;

import com.kisf.sqlquery.admin.entity.SqlConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SqlConfigRepositoryTest {

    @Autowired
    private SqlConfigRepository repo;

    @Test
    void shouldSaveAndFindBySqlPath() {
        SqlConfig config = new SqlConfig();
        config.setSqlPath("/order/detail");
        config.setSqlTemplate("<select id=\"q\">SELECT * FROM orders WHERE id = #{orderId}</select>");
        config.setDatasourceId("order_db");
        config.setEnabled(true);
        repo.save(config);

        Optional<SqlConfig> found = repo.findBySqlPath("/order/detail");
        assertThat(found).isPresent();
        assertThat(found.get().getDatasourceId()).isEqualTo("order_db");
    }
}
