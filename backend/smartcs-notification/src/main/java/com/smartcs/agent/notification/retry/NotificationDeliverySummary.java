package com.smartcs.agent.notification.retry;

import java.time.Instant;

/** Notification 投递 worker 的只读运行摘要。 */
public record NotificationDeliverySummary(
        boolean enabled,
        long accepted,
        long retryableFailed,
        long exhaustedFailed,
        long delivered,
        long due,
        long leased,
        Instant oldestDueAt
) {

    public static NotificationDeliverySummary disabled() {
        return new NotificationDeliverySummary(false, 0, 0, 0, 0, 0, 0, null);
    }
}
