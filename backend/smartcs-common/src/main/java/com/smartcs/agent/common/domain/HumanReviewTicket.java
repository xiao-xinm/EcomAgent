package com.smartcs.agent.common.domain;

import com.smartcs.agent.common.enums.RiskLevel;
import com.smartcs.agent.common.enums.RouteDecision;
import com.smartcs.agent.common.enums.TicketPriority;
import com.smartcs.agent.common.enums.TicketStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

/**
 * Contract for creating or transferring a human review ticket.
 */
public record HumanReviewTicket(
        @NotBlank String ticketId,
        @NotBlank String traceId,
        @NotBlank String sessionId,
        @NotBlank String userId,
        @NotBlank String intent,
        @NotNull RiskLevel riskLevel,
        @NotNull RouteDecision routeDecision,
        @Valid DialogContext contextSnapshot,
        String reason,
        @NotNull TicketStatus status,
        @NotNull TicketPriority priority,
        Map<String, Object> metadata,
        @NotNull Instant createdAt
) {
}
