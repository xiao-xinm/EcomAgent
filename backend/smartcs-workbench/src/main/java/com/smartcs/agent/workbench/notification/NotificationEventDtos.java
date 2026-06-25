package com.smartcs.agent.workbench.notification;

import java.time.Instant;
import java.util.Map;

/**
 * Local DTOs used by Workbench to publish notification events.
 */
public final class NotificationEventDtos {

    private NotificationEventDtos() {
    }

    public record NotificationEventRequest(
            String eventId,
            String traceId,
            String sourceService,
            String eventType,
            String recipientUserId,
            String sessionId,
            String ticketId,
            String operatorId,
            String channel,
            String title,
            String content,
            Map<String, Object> payload,
            Instant occurredAt
    ) {
    }

    public record NotificationEventResult(
            String eventId,
            String status,
            String channel,
            Instant acceptedAt
    ) {
    }
}
