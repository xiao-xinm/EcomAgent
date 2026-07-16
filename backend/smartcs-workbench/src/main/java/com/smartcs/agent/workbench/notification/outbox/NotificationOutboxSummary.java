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
        Instant oldestDueAt,
        boolean notificationEnabled,
        String userMessageDeliveryMode
) {

    public NotificationOutboxSummary(
            boolean enabled,
            long pending,
            long retryableFailed,
            long exhaustedFailed,
            long sent,
            long due,
            long leased,
            Instant oldestDueAt) {
        this(enabled, pending, retryableFailed, exhaustedFailed, sent, due, leased, oldestDueAt, false, "UNKNOWN");
    }

    public static NotificationOutboxSummary disabled(boolean notificationEnabled, String deliveryMode) {
        return new NotificationOutboxSummary(
                false, 0, 0, 0, 0, 0, 0, null, notificationEnabled, deliveryMode);
    }

    public NotificationOutboxSummary withRuntimeState(boolean notificationEnabled, String deliveryMode) {
        return new NotificationOutboxSummary(
                enabled,
                pending,
                retryableFailed,
                exhaustedFailed,
                sent,
                due,
                leased,
                oldestDueAt,
                notificationEnabled,
                deliveryMode);
    }
}
