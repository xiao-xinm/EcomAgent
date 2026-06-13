package com.smartcs.agent.common.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

/**
 * General audit event contract. Event data must be desensitized by producer.
 */
public record AuditEvent(
        @NotBlank String eventId,
        @NotBlank String traceId,
        String sessionId,
        String ticketId,
        String userId,
        String operatorId,
        @NotBlank String eventType,
        Map<String, Object> eventData,
        @NotNull Instant occurredAt
) {
}
