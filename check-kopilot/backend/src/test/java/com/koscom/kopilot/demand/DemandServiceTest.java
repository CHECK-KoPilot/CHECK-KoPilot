package com.koscom.kopilot.demand;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("fixture")
class DemandServiceTest {

    @Autowired DemandService demand;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() { jdbc.update("DELETE FROM catalog_request WHERE session_id LIKE 'test-%'"); }

    @Test
    void recordsAutoAndExplicitRowsWithSource() {
        demand.record("test-s1", "외국인 순매수 수급", "stock-investor", "AUTO");
        demand.record("test-s1", "외국인 순매수 수급", "stock-investor", "EXPLICIT");

        var rows = jdbc.queryForList(
                "SELECT source, topic, matched_api_ids FROM catalog_request WHERE session_id = ? ORDER BY id",
                "test-s1");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("source")).isEqualTo("AUTO");
        assertThat(rows.get(1).get("source")).isEqualTo("EXPLICIT");
        assertThat(rows.get(0).get("topic")).isEqualTo("외국인 순매수 수급");
    }

    @Test
    void longTopicIsTruncatedToColumnLimit() {
        demand.record("test-s2", "가".repeat(400), null, "AUTO");
        String topic = jdbc.queryForObject(
                "SELECT topic FROM catalog_request WHERE session_id = ?", String.class, "test-s2");
        assertThat(topic).hasSize(255);
    }

    @Test
    void blankTopicIsIgnored() {
        demand.record("test-s3", "   ", null, "AUTO");
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM catalog_request WHERE session_id = ?", Integer.class, "test-s3");
        assertThat(count).isZero();
    }
}
