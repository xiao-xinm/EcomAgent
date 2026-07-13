package com.smartcs.agent.notification.retry;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 通知失败后的有界退避策略。 */
public class NotificationRetryPolicy {

    private static final List<Duration> BACKOFFS = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            Duration.ofMinutes(30));

    public RetryDecision afterFailure(int currentRetryCount, int maxAttempts, Instant now) {
        if (currentRetryCount < 0) {
            throw new IllegalArgumentException("currentRetryCount 不能小于 0");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts 必须大于 0");
        }
        int failedAttempts = currentRetryCount + 1;
        if (failedAttempts >= maxAttempts) {
            return new RetryDecision(failedAttempts, true, null);
        }
        Duration delay = BACKOFFS.get(Math.min(failedAttempts - 1, BACKOFFS.size() - 1));
        return new RetryDecision(failedAttempts, false, now.plus(delay));
    }

    public record RetryDecision(
            int failedAttempts,
            boolean exhausted,
            Instant nextRetryAt
    ) {
    }
}
