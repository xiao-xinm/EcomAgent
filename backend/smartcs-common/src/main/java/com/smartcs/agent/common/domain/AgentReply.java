package com.smartcs.agent.common.domain;

import com.smartcs.agent.common.enums.MessageType;
import com.smartcs.agent.common.enums.RiskLevel;
import com.smartcs.agent.common.enums.RouteDecision;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Agent response contract returned to gateway or workbench.
 */
public record AgentReply(
        @NotBlank String replyId,
        @NotBlank String traceId,
        @NotBlank String sessionId,
        @NotNull MessageType messageType,
        String content,
        List<@Valid QuickAction> quickActions,
        @NotNull RiskLevel riskLevel,
        @NotNull RouteDecision routeDecision,
        String ticketId,
        Map<String, Object> metadata,
        @NotNull Instant createdAt
) {
}
