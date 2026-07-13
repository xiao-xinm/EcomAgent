package com.smartcs.agent.notification.retry;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcs.agent.notification.retry.NotificationRetryPolicy.RetryDecision;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class NotificationRetryPolicyTest {

    private static final Instant NOW = Instant.parse("2026-07-13T06:00:00Z");

    private final NotificationRetryPolicy policy = new NotificationRetryPolicy();

    @Test
    void failuresUseBoundedBackoffSequence() {
        assertThat(policy.afterFailure(0, 5, NOW).nextRetryAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(policy.afterFailure(1, 5, NOW).nextRetryAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(policy.afterFailure(2, 5, NOW).nextRetryAt()).isEqualTo(NOW.plusSeconds(900));
        assertThat(policy.afterFailure(3, 5, NOW).nextRetryAt()).isEqualTo(NOW.plusSeconds(1800));
    }

    @Test
    void maxAttemptStopsSchedulingAnotherRetry() {
        RetryDecision decision = policy.afterFailure(4, 5, NOW);

        assertThat(decision.failedAttempts()).isEqualTo(5);
        assertThat(decision.exhausted()).isTrue();
        assertThat(decision.nextRetryAt()).isNull();
    }
}
