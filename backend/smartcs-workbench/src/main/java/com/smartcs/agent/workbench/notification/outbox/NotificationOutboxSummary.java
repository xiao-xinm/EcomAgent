package com.smartcs.agent.workbench.notification.outbox;

import java.time.Instant;

/** Workbench 通知 outbox 的只读运行摘要。 */
public record NotificationOutboxSummary(
        boolean enabled,
        long pending,
        long retryableFailed,
        long exhaustedFailed,
        long sent,
        long due,
        long leased,
        Instant oldestDueAt
) {

    public static NotificationOutboxSummary disabled() {
        return new NotificationOutboxSummary(false, 0, 0, 0, 0, 0, 0, null);
    }
}
