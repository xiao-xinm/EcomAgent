package com.smartcs.agent.core.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcs.agent.common.domain.AgentReply;
import com.smartcs.agent.common.domain.ChatRequest;
import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.common.enums.ErrorCode;
import com.smartcs.agent.common.enums.MessageType;
import com.smartcs.agent.common.enums.RiskLevel;
import com.smartcs.agent.common.enums.RouteDecision;
import com.smartcs.agent.core.service.ChatSessionQueryService;
import com.smartcs.agent.core.service.MockAgentOrchestrator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentChatControllerTest {

    @Test
    void chatWrapsAgentReplyWithSameTraceId() {
        MockAgentOrchestrator orchestrator = mock(MockAgentOrchestrator.class);
        ChatSessionQueryService sessionQueryService = mock(ChatSessionQueryService.class);
        AgentChatController controller = new AgentChatController(orchestrator, sessionQueryService);
        ChatRequest request = new ChatRequest("trace_test", "s_test", "u1001", "h5", "我的订单", Map.of());
        AgentReply reply = new AgentReply(
                "r_test",
                "trace_test",
                "s_test",
                MessageType.TEXT,
                "已查询到订单。",
                List.of(),
                RiskLevel.L0,
                RouteDecision.AUTO_REPLY,
                null,
                Map.of("intent", "order.query", "skillExecutionId", "se_test"),
                Instant.now());
        when(orchestrator.process(request)).thenReturn(reply);

        ApiResponse<AgentReply> response = controller.chat(request);

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.traceId()).isEqualTo("trace_test");
        assertThat(response.data()).isSameAs(reply);
        assertThat(response.data().routeDecision()).isEqualTo(RouteDecision.AUTO_REPLY);
        verify(orchestrator).process(request);
    }

    @Test
    void getSessionReturnsNotFoundEnvelopeWhenSessionMissing() {
        MockAgentOrchestrator orchestrator = mock(MockAgentOrchestrator.class);
        ChatSessionQueryService sessionQueryService = mock(ChatSessionQueryService.class);
        AgentChatController controller = new AgentChatController(orchestrator, sessionQueryService);
        when(sessionQueryService.findSession("missing_session")).thenReturn(java.util.Optional.empty());

        ApiResponse<?> response = controller.getSession("missing_session");

        assertThat(response.code()).isEqualTo(ErrorCode.NOT_FOUND.code());
        assertThat(response.data()).isNull();
        assertThat(response.traceId()).isNotBlank();
    }
}
