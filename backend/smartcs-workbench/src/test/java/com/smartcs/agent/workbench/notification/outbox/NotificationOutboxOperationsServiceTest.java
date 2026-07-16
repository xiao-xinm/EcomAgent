package com.smartcs.agent.workbench.notification.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartcs.agent.workbench.notification.UserMessageDeliveryPolicy;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class NotificationOutboxOperationsServiceTest {

    @Test
    void disabledOutboxReturnsZeroSummaryWithoutDatabaseAccess() {
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        NotificationOutboxOperationsService service =
                new NotificationOutboxOperationsService(
                        repository, new UserMessageDeliveryPolicy(true, false), false, true, 5);

        NotificationOutboxSummary summary = service.getSummary();

        assertThat(summary.enabled()).isFalse();
        assertThat(summary.notificationEnabled()).isTrue();
        assertThat(summary.userMessageDeliveryMode()).isEqualTo("DIRECT");
        assertThat(summary.pending()).isZero();
        assertThat(summary.oldestDueAt()).isNull();
        verifyNoInteractions(repository);
    }

    @Test
    void enabledOutboxReturnsRepositorySummary() {
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        NotificationOutboxSummary expected = new NotificationOutboxSummary(
                true, 3, 2, 1, 12, 4, 1, Instant.parse("2026-07-13T08:00:00Z"));
        when(repository.summarize(5)).thenReturn(expected);
        NotificationOutboxOperationsService service =
                new NotificationOutboxOperationsService(
                        repository, new UserMessageDeliveryPolicy(false, true), true, true, 5);

        NotificationOutboxSummary summary = service.getSummary();

        assertThat(summary.pending()).isEqualTo(expected.pending());
        assertThat(summary.notificationEnabled()).isTrue();
        assertThat(summary.userMessageDeliveryMode()).isEqualTo("NOTIFICATION");
        verify(repository).summarize(5);
    }

    @Test
    void invalidMaxAttemptsFailsFast() {
        assertThatThrownBy(() -> new NotificationOutboxOperationsService(
                        mock(NotificationOutboxRepository.class),
                        new UserMessageDeliveryPolicy(true, true),
                        true,
                        true,
                        0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts");
    }
}
