package com.smartcs.agent.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcs.agent.common.domain.AgentReply;
import com.smartcs.agent.common.domain.ChatActionRequest;
import com.smartcs.agent.common.domain.ChatRequest;
import com.smartcs.agent.common.enums.RouteDecision;
import com.smartcs.agent.core.knowledge.KnowledgeFaqClient;
import com.smartcs.agent.core.knowledge.KnowledgeFaqDtos.FaqQueryRequest;
import com.smartcs.agent.core.knowledge.KnowledgeFaqDtos.FaqQueryResult;
import com.smartcs.agent.core.skill.SkillEngineClient;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class MockAgentOrchestratorTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void requestHumanActionCreatesHumanTakeoverReply() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(contains("FROM cs_session"), any(RowMapper.class), eq("s_faq")))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("session_id")).thenReturn("s_faq");
                    when(rs.getString("trace_id")).thenReturn("trace_faq");
                    when(rs.getString("user_id")).thenReturn("u1001");
                    when(rs.getString("channel")).thenReturn("h5");
                    when(rs.getString("status")).thenReturn("ACTIVE");
                    when(rs.getString("dialog_state")).thenReturn("ACTIVE");
                    when(rs.getString("current_intent")).thenReturn("faq.query");
                    return List.of(mapper.mapRow(rs, 0));
                });
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        MockAgentOrchestrator orchestrator = new MockAgentOrchestrator(
                jdbcTemplate,
                new ObjectMapper(),
                mock(SkillEngineClient.class),
                mock(KnowledgeFaqClient.class));

        AgentReply reply = orchestrator.processAction(new ChatActionRequest(
                "trace_action",
                "s_faq",
                "u1001",
                "h5",
                "request_human",
                "REQUEST_HUMAN",
                "转人工客服",
                Map.of("reasonCode", "FAQ_NOT_MATCHED"),
                Map.of()));

        assertThat(reply.routeDecision()).isEqualTo(RouteDecision.HUMAN_TAKEOVER);
        assertThat(reply.ticketId()).startsWith("wo_");
        assertThat(reply.metadata()).containsEntry("reasonCode", "FAQ_USER_REQUEST_HUMAN");
        assertThat(reply.metadata()).containsEntry("sourceIntent", "faq.query");
        verify(jdbcTemplate).update(contains("INSERT INTO human_takeover"), any(Object[].class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void faqMissReturnsHumanFallbackQuickAction() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(contains("FROM skill_registry"), any(RowMapper.class), eq("faq.query")))
                .thenReturn(List.of());
        when(jdbcTemplate.query(contains("FROM risk_rule"), any(RowMapper.class), eq("faq.query")))
                .thenReturn(List.of());
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        KnowledgeFaqClient knowledgeFaqClient = mock(KnowledgeFaqClient.class);
        when(knowledgeFaqClient.query(any(FaqQueryRequest.class))).thenReturn(Optional.of(new FaqQueryResult(
                "faq_unmatched",
                "地址规则是什么",
                "这个问题暂时没有稳定答案。",
                false,
                0.0D,
                List.of(),
                "faq-keyword-v1",
                Instant.now())));

        MockAgentOrchestrator orchestrator = new MockAgentOrchestrator(
                jdbcTemplate,
                new ObjectMapper(),
                mock(SkillEngineClient.class),
                knowledgeFaqClient);

        AgentReply reply = orchestrator.process(new ChatRequest(
                "trace_faq",
                "s_faq",
                "u1001",
                "h5",
                "地址规则是什么",
                Map.of()));

        assertThat(reply.routeDecision()).isEqualTo(RouteDecision.AUTO_REPLY);
        assertThat(reply.metadata()).containsEntry("knowledgeFallback", true);
        assertThat(reply.metadata()).containsEntry("knowledgeMissReason", "FAQ_NOT_MATCHED");
        assertThat(reply.metadata()).containsEntry("knowledgeMissStrategy", "REPHRASE_OR_REQUEST_HUMAN");
        assertThat(reply.quickActions())
                .anySatisfy(action -> {
                    assertThat(action.value()).isEqualTo("request_human");
                    assertThat(action.actionType()).isEqualTo("REQUEST_HUMAN");
                });
    }
}
