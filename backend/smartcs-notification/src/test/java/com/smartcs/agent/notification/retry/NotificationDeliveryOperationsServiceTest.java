package com.smartcs.agent.notification.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class NotificationDeliveryOperationsServiceTest {

    @Test
    void disabledRetryReturnsZeroSummaryWithoutDatabaseAccess() {
        NotificationRetryRepository repository = mock(NotificationRetryRepository.class);
        NotificationDeliveryOperationsService service =
                new NotificationDeliveryOperationsService(repository, false, false, 5);

        NotificationDeliverySummary summary = service.getSummary();

        assertThat(summary.enabled()).isFalse();
        assertThat(summary.userSessionChannelEnabled()).isFalse();
        assertThat(summary.accepted()).isZero();
        assertThat(summary.oldestDueAt()).isNull();
        verifyNoInteractions(repository);
    }

    @Test
    void enabledRetryReturnsRepositorySummary() {
        NotificationRetryRepository repository = mock(NotificationRetryRepository.class);
        NotificationDeliverySummary expected = new NotificationDeliverySummary(
                true, 3, 2, 1, 12, 4, 1, Instant.parse("2026-07-13T08:00:00Z"));
        when(repository.summarize(5)).thenReturn(expected);
        NotificationDeliveryOperationsService service =
                new NotificationDeliveryOperationsService(repository, true, true, 5);

        NotificationDeliverySummary summary = service.getSummary();

        assertThat(summary.accepted()).isEqualTo(expected.accepted());
        assertThat(summary.userSessionChannelEnabled()).isTrue();
        verify(repository).summarize(5);
    }

    @Test
    void invalidMaxAttemptsFailsFast() {
        assertThatThrownBy(() -> new NotificationDeliveryOperationsService(
                        mock(NotificationRetryRepository.class), true, true, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts");
    }
}
