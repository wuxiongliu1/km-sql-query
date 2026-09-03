package com.kisf.sqlquery.core.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatasourceConfigTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldAcceptPasswordButNeverSerializeIt() throws Exception {
        DatasourceConfig config = objectMapper.readValue(
                "{\"id\":\"main\",\"password\":\"secret\"}", DatasourceConfig.class);

        assertThat(config.getPassword()).isEqualTo("secret");
        assertThat(objectMapper.writeValueAsString(config)).doesNotContain("password", "secret");
    }
}
