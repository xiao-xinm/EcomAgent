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
        Instant oldestDueAt,
        boolean userSessionChannelEnabled
) {

    public NotificationDeliverySummary(
            boolean enabled,
            long accepted,
            long retryableFailed,
            long exhaustedFailed,
            long delivered,
            long due,
            long leased,
            Instant oldestDueAt) {
        this(enabled, accepted, retryableFailed, exhaustedFailed, delivered, due, leased, oldestDueAt, false);
    }

    public static NotificationDeliverySummary disabled(boolean userSessionChannelEnabled) {
        return new NotificationDeliverySummary(false, 0, 0, 0, 0, 0, 0, null, userSessionChannelEnabled);
    }

    public NotificationDeliverySummary withUserSessionChannel(boolean enabled) {
        return new NotificationDeliverySummary(
                this.enabled,
                accepted,
                retryableFailed,
                exhaustedFailed,
                delivered,
                due,
                leased,
                oldestDueAt,
                enabled);
    }
}
