package com.smartcs.agent.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcs.agent.knowledge.dto.FaqQueryDtos.FaqQueryRequest;
import com.smartcs.agent.knowledge.dto.FaqQueryDtos.FaqQueryResponse;
import com.smartcs.agent.knowledge.dto.FaqAdminDtos.FaqItem;
import com.smartcs.agent.knowledge.dto.FaqAdminDtos.FaqStatusRequest;
import com.smartcs.agent.knowledge.indexing.KnowledgeIndexSynchronizer;
import com.smartcs.agent.knowledge.retrieval.HybridKnowledgeRetriever;
import com.smartcs.agent.knowledge.retrieval.HybridKnowledgeRetriever.HybridRetrievalResult;
import com.smartcs.agent.knowledge.retrieval.KnowledgeRetrievalCandidate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class FaqKnowledgeServiceTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void updateStatusSynchronizesSearchIndexesAfterMysqlUpdate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        KnowledgeIndexSynchronizer synchronizer = mock(KnowledgeIndexSynchronizer.class);
        Instant now = Instant.parse("2026-07-11T07:00:00Z");
        FaqItem item = new FaqItem(
                "faq_refund_arrival",
                "退款多久到账",
                "退款通常会在审核后原路退回。",
                List.of("退款", "到账"),
                "after_sale",
                "DISABLED",
                100,
                now,
                now);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(item));
        when(synchronizer.synchronize(item)).thenReturn(
                new KnowledgeIndexSynchronizer.IndexSyncSummary(true, 1, 2, 0, List.of()));
        FaqKnowledgeService service = new FaqKnowledgeService(
                jdbcTemplate,
                new ObjectMapper(),
                null,
                synchronizer);

        FaqItem result = service.updateStatus("faq_refund_arrival", new FaqStatusRequest("DISABLED"));

        assertThat(result).isSameAs(item);
        verify(synchronizer).synchronize(item);
    }

    @Test
    void queryUsesHybridResultBeforeMysqlKeywordFallback() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        HybridKnowledgeRetriever retriever = mock(HybridKnowledgeRetriever.class);
        when(retriever.retrieve("退钱一般要等几天")).thenReturn(Optional.of(new HybridRetrievalResult(
                new KnowledgeRetrievalCandidate(
                        "faq_refund_arrival",
                        "退款多久到账",
                        "退款通常会在审核后原路退回。",
                        "after_sale",
                        "hybrid-rrf-v1",
                        0.03D),
                0.90D,
                "hybrid-rrf-v1",
                0.03D,
                List.of("elasticsearch-bm25-v1", "pgvector-cosine-v1"))));
        FaqKnowledgeService service = new FaqKnowledgeService(jdbcTemplate, new ObjectMapper(), retriever);

        FaqQueryResponse response = service.query(new FaqQueryRequest(
                "trace_faq",
                "s_test",
                "u1001",
                "h5",
                "退钱一般要等几天"));

        assertThat(response.matched()).isTrue();
        assertThat(response.answerId()).isEqualTo("faq_refund_arrival");
        assertThat(response.source()).isEqualTo("hybrid-rrf-v1");
        assertThat(response.confidence()).isEqualTo(0.90D);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void queryFallsBackToBuiltinFaqsWhenKnowledgeTableIsUnavailable() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenThrow(new BadSqlGrammarException("query", "SELECT * FROM knowledge_faq", null));
        FaqKnowledgeService service = new FaqKnowledgeService(jdbcTemplate, new ObjectMapper());

        FaqQueryResponse response = service.query(new FaqQueryRequest(
                "trace_faq",
                "s_test",
                "u1001",
                "h5",
                "退款多久到账"));

        assertThat(response.matched()).isTrue();
        assertThat(response.answerId()).isEqualTo("faq_builtin_refund_arrival");
        assertThat(response.source()).isEqualTo("faq-keyword-v1");
        assertThat(response.matchedKeywords()).contains("退款", "到账");
    }
}
