package com.smartcs.agent.workbench.notification.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Workbench 通知 outbox 的有限退避策略。 */
public class NotificationOutboxRetryPolicy {

    private static final List<Duration> BACKOFFS = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            Duration.ofMinutes(30));

    public RetryDecision afterFailure(int currentAttemptCount, int maxAttempts, Instant now) {
        if (currentAttemptCount < 0) {
            throw new IllegalArgumentException("currentAttemptCount 不能小于 0");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts 必须大于 0");
        }
        int failedAttempts = currentAttemptCount + 1;
        if (failedAttempts >= maxAttempts) {
            return new RetryDecision(failedAttempts, true, null);
        }
        Duration delay = BACKOFFS.get(Math.min(failedAttempts - 1, BACKOFFS.size() - 1));
        return new RetryDecision(failedAttempts, false, now.plus(delay));
    }

    public record RetryDecision(
            int failedAttempts,
            boolean exhausted,
            Instant nextAttemptAt
    ) {
    }
}
