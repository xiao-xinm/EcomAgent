package com.smartcs.agent.knowledge.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcs.agent.knowledge.dto.KnowledgeIndexStatusDtos.KnowledgeIndexStatus;
import com.smartcs.agent.knowledge.indexing.KnowledgeIndexProbe.ProbeResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class KnowledgeIndexStatusServiceTest {

    @Test
    void keywordModeChecksOnlyMysqlSource() {
        JdbcTemplate jdbcTemplate = mysqlWithActiveCount(7L);
        KnowledgeIndexProbe probe = mock(KnowledgeIndexProbe.class);
        KnowledgeIndexStatusService service =
                new KnowledgeIndexStatusService(jdbcTemplate, List.of(probe), "keyword");

        KnowledgeIndexStatus status = service.inspect();

        assertThat(status.mode()).isEqualTo("keyword");
        assertThat(status.enabled()).isFalse();
        assertThat(status.healthy()).isTrue();
        assertThat(status.consistent()).isTrue();
        assertThat(status.source().documentCount()).isEqualTo(7L);
        assertThat(status.indexes()).isEmpty();
        verify(probe, never()).probe();
    }

    @Test
    void hybridModeIsHealthyWhenBothIndexesMatchMysql() {
        JdbcTemplate jdbcTemplate = mysqlWithActiveCount(7L);
        KnowledgeIndexStatusService service = new KnowledgeIndexStatusService(
                jdbcTemplate,
                List.of(
                        probe("elasticsearch", ProbeResult.ready("elasticsearch", 7L)),
                        probe("pgvector", ProbeResult.ready("pgvector", 7L))),
                "HYBRID");

        KnowledgeIndexStatus status = service.inspect();

        assertThat(status.enabled()).isTrue();
        assertThat(status.healthy()).isTrue();
        assertThat(status.consistent()).isTrue();
        assertThat(status.indexes()).extracting(index -> index.documentCount())
                .containsExactly(7L, 7L);
    }

    @Test
    void hybridModeReportsInconsistentWhenAnIndexIsMissingOrStale() {
        JdbcTemplate jdbcTemplate = mysqlWithActiveCount(7L);
        KnowledgeIndexStatusService service = new KnowledgeIndexStatusService(
                jdbcTemplate,
                List.of(probe("elasticsearch", ProbeResult.ready("elasticsearch", 6L))),
                "hybrid");

        KnowledgeIndexStatus status = service.inspect();

        assertThat(status.healthy()).isFalse();
        assertThat(status.consistent()).isFalse();
        assertThat(status.indexes().get(0).documentCount()).isEqualTo(6L);
        assertThat(status.indexes().get(1).available()).isFalse();
        assertThat(status.indexes().get(1).message()).isEqualTo("probe unavailable");
    }

    @Test
    void sourceFailureIsReturnedAsStatusInsteadOfEscaping() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenThrow(new DataAccessResourceFailureException("mysql unavailable"));
        KnowledgeIndexStatusService service =
                new KnowledgeIndexStatusService(jdbcTemplate, List.of(), "keyword");

        KnowledgeIndexStatus status = service.inspect();

        assertThat(status.healthy()).isFalse();
        assertThat(status.source().available()).isFalse();
        assertThat(status.source().message()).isEqualTo("mysql unavailable");
    }

    private JdbcTemplate mysqlWithActiveCount(long count) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(count);
        return jdbcTemplate;
    }

    private KnowledgeIndexProbe probe(String name, ProbeResult result) {
        KnowledgeIndexProbe probe = mock(KnowledgeIndexProbe.class);
        when(probe.name()).thenReturn(name);
        when(probe.probe()).thenReturn(result);
        return probe;
    }
}
