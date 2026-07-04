package com.smartcs.agent.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcs.agent.knowledge.dto.FaqQueryDtos.FaqQueryRequest;
import com.smartcs.agent.knowledge.dto.FaqQueryDtos.FaqQueryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class FaqKnowledgeServiceTest {

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
