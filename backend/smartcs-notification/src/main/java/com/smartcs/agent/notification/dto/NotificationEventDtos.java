package com.smartcs.agent.notification.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Notification event API contracts.
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

    public record NotificationEventView(
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
            String status,
            Instant occurredAt,
            Instant acceptedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
