package com.smartcs.agent.notification.delivery;

import java.time.Instant;
import java.util.Map;

/** 传递给具体通知通道的稳定投递命令。 */
public record NotificationDeliveryCommand(
        String eventId,
        String traceId,
        String eventType,
        String recipientUserId,
        String sessionId,
        String ticketId,
        String operatorId,
        String channel,
        String title,
        String content,
        Map<String, Object> payload,
        Instant occurredAt,
        int attempt
) {
}
