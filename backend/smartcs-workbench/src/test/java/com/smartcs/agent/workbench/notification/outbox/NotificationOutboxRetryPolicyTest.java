package com.smartcs.agent.workbench.notification.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class NotificationOutboxRetryPolicyTest {

    private static final Instant NOW = Instant.parse("2026-07-13T07:00:00Z");
    private final NotificationOutboxRetryPolicy policy = new NotificationOutboxRetryPolicy();

    @Test
    void failuresUseBoundedBackoffSequence() {
        assertThat(policy.afterFailure(0, 6, NOW).nextAttemptAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(policy.afterFailure(1, 6, NOW).nextAttemptAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(policy.afterFailure(2, 6, NOW).nextAttemptAt()).isEqualTo(NOW.plusSeconds(900));
        assertThat(policy.afterFailure(3, 6, NOW).nextAttemptAt()).isEqualTo(NOW.plusSeconds(1800));
        assertThat(policy.afterFailure(4, 6, NOW).nextAttemptAt()).isEqualTo(NOW.plusSeconds(1800));
    }

    @Test
    void maxAttemptStopsSchedulingAnotherDelivery() {
        NotificationOutboxRetryPolicy.RetryDecision decision = policy.afterFailure(4, 5, NOW);

        assertThat(decision.failedAttempts()).isEqualTo(5);
        assertThat(decision.exhausted()).isTrue();
        assertThat(decision.nextAttemptAt()).isNull();
    }
}
