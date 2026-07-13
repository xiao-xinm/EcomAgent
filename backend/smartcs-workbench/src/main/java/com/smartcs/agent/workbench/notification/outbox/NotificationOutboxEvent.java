package com.smartcs.agent.workbench.notification.outbox;

import com.smartcs.agent.workbench.notification.NotificationEventDtos.NotificationEventRequest;

/** 从 Workbench 通知 outbox 读取的待投递事件。 */
public record NotificationOutboxEvent(
        String eventId,
        NotificationEventRequest request,
        int attemptCount
) {
}
