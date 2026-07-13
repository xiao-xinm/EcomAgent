package com.smartcs.agent.notification.retry;

import com.smartcs.agent.notification.delivery.NotificationDeliveryCommand;
import java.time.Instant;
import java.util.Map;

/** 从 notification_event 读取的待投递事件快照。 */
public record NotificationRetryEvent(
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
        int retryCount,
        Instant occurredAt
) {

    public NotificationDeliveryCommand toCommand() {
        return new NotificationDeliveryCommand(
                eventId,
                traceId,
                eventType,
                recipientUserId,
                sessionId,
                ticketId,
                operatorId,
                channel,
                title,
                content,
                payload,
                occurredAt,
                retryCount + 1);
    }
}
