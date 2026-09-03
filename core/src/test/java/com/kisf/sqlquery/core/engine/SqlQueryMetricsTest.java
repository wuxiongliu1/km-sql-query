package com.kisf.sqlquery.core.engine;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlQueryMetricsTest {

    @Test
    void shouldRecordDurationOutcomeAndReturnedRows() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SqlQueryMetrics metrics = new SqlQueryMetrics(registry, Long.MAX_VALUE);

        metrics.record("/orders/list", 25, true, 3);
        metrics.record("/orders/list", 10, false, 0);

        assertThat(registry.get("km.sql.query.duration")
                .tags("sqlPath", "/orders/list", "outcome", "success")
                .timer().count()).isEqualTo(1);
        assertThat(registry.get("km.sql.query.duration")
                .tags("sqlPath", "/orders/list", "outcome", "failure")
                .timer().count()).isEqualTo(1);
        assertThat(registry.get("km.sql.query.returned.rows")
                .tag("sqlPath", "/orders/list").summary().totalAmount()).isEqualTo(3);
    }
}
