package com.smartcs.agent.common.domain;

import com.smartcs.agent.common.enums.TicketActionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

/**
 * Auditable action performed on a human review ticket.
 */
public record TicketAction(
        @NotBlank String actionId,
        @NotBlank String ticketId,
        @NotBlank String traceId,
        @NotBlank String operatorId,
        @NotNull TicketActionType actionType,
        String comment,
        Map<String, Object> actionData,
        @NotNull Instant createdAt
) {
}
