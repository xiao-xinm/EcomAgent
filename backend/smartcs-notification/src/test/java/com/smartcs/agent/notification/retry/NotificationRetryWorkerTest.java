package com.smartcs.agent.notification.retry;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcs.agent.notification.delivery.NotificationDeliveryChannel;
import com.smartcs.agent.notification.delivery.NotificationDeliveryCommand;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationRetryWorkerTest {

    private static final Instant NOW = Instant.parse("2026-07-13T06:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void successfulDeliveryMarksEventDelivered() {
        NotificationRetryRepository repository = mock(NotificationRetryRepository.class);
        NotificationDeliveryChannel channel = channel("USER_SESSION");
        NotificationRetryEvent event = event(0, "USER_SESSION");
        when(repository.claimDue("worker-test", 20, 5, 120000)).thenReturn(List.of(event));
        when(repository.markDelivered("ntf_test", "worker-test")).thenReturn(1);
        NotificationRetryWorker worker = worker(repository, List.of(channel), 5);

        worker.runOnce();

        verify(channel).deliver(any(NotificationDeliveryCommand.class));
        verify(repository).markDelivered("ntf_test", "worker-test");
    }

    @Test
    void channelFailureSchedulesFirstRetryAfterOneMinute() {
        NotificationRetryRepository repository = mock(NotificationRetryRepository.class);
        NotificationDeliveryChannel channel = channel("USER_SESSION");
        doThrow(new IllegalStateException("channel unavailable"))
                .when(channel)
                .deliver(any(NotificationDeliveryCommand.class));
        when(repository.claimDue("worker-test", 20, 5, 120000))
                .thenReturn(List.of(event(0, "USER_SESSION")));
        when(repository.markFailed(
                        "ntf_test", "worker-test", "channel unavailable", NOW.plusSeconds(60)))
                .thenReturn(1);
        NotificationRetryWorker worker = worker(repository, List.of(channel), 5);

        worker.runOnce();

        verify(repository).markFailed(
                "ntf_test", "worker-test", "channel unavailable", NOW.plusSeconds(60));
    }

    @Test
    void unsupportedChannelDoesNotPretendDeliverySucceeded() {
        NotificationRetryRepository repository = mock(NotificationRetryRepository.class);
        when(repository.claimDue("worker-test", 20, 5, 120000)).thenReturn(List.of(event(0, "SMS")));
        when(repository.markFailed(
                        "ntf_test", "worker-test", "UNSUPPORTED_CHANNEL: SMS", NOW.plusSeconds(60)))
                .thenReturn(1);
        NotificationRetryWorker worker = worker(repository, List.of(channel("USER_SESSION")), 5);

        worker.runOnce();

        verify(repository).markFailed(
                "ntf_test", "worker-test", "UNSUPPORTED_CHANNEL: SMS", NOW.plusSeconds(60));
    }

    @Test
    void exhaustedEventIsFailedWithoutAnotherSchedule() {
        NotificationRetryRepository repository = mock(NotificationRetryRepository.class);
        NotificationDeliveryChannel channel = channel("USER_SESSION");
        doThrow(new IllegalStateException("still unavailable"))
                .when(channel)
                .deliver(any(NotificationDeliveryCommand.class));
        when(repository.claimDue("worker-test", 20, 5, 120000))
                .thenReturn(List.of(event(4, "USER_SESSION")));
        when(repository.markFailed("ntf_test", "worker-test", "still unavailable", null))
                .thenReturn(1);
        NotificationRetryWorker worker = worker(repository, List.of(channel), 5);

        worker.runOnce();

        verify(repository).markFailed("ntf_test", "worker-test", "still unavailable", null);
    }

    @Test
    void duplicateChannelNamesAreRejectedAtStartup() {
        NotificationRetryRepository repository = mock(NotificationRetryRepository.class);

        assertThatThrownBy(() -> worker(
                        repository,
                        List.of(channel("USER_SESSION"), channel("user_session")),
                        5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复通知通道");
    }

    @Test
    void missingDeliveryChannelIsRejectedAtStartup() {
        NotificationRetryRepository repository = mock(NotificationRetryRepository.class);

        assertThatThrownBy(() -> worker(repository, List.of(), 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少需要注册一个通知投递通道");
    }

    private NotificationRetryWorker worker(
            NotificationRetryRepository repository,
            List<NotificationDeliveryChannel> channels,
            int maxAttempts) {
        return new NotificationRetryWorker(
                repository,
                channels,
                new NotificationRetryPolicy(),
                20,
                maxAttempts,
                120000,
                "worker-test",
                CLOCK);
    }

    private NotificationDeliveryChannel channel(String name) {
        NotificationDeliveryChannel channel = mock(NotificationDeliveryChannel.class);
        when(channel.channel()).thenReturn(name);
        return channel;
    }

    private NotificationRetryEvent event(int retryCount, String channel) {
        return new NotificationRetryEvent(
                "ntf_test",
                "trace_test",
                "APPROVAL_APPROVED",
                "u1001",
                "s_test",
                "wo_test",
                "agent001",
                channel,
                "审批通过通知",
                "审批已通过",
                Map.of("decision", "APPROVED"),
                retryCount,
                NOW);
    }
}
