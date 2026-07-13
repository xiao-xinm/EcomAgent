package com.smartcs.agent.workbench.notification.outbox;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcs.agent.workbench.notification.NotificationEventClient;
import com.smartcs.agent.workbench.notification.NotificationEventDtos.NotificationEventRequest;
import com.smartcs.agent.workbench.notification.NotificationEventDtos.NotificationEventResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NotificationOutboxWorkerTest {

    private static final Instant NOW = Instant.parse("2026-07-13T07:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void acceptedNotificationMarksOutboxEventSent() {
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        NotificationEventClient client = mock(NotificationEventClient.class);
        NotificationOutboxEvent event = event(0);
        when(repository.findDue(20, 5)).thenReturn(List.of(event));
        when(client.publish(event.request())).thenReturn(Optional.of(new NotificationEventResult(
                "ntf_test", "ACCEPTED", "USER_SESSION", NOW)));

        worker(repository, client, 5, true).runOnce();

        verify(repository).markSent("ntf_test");
    }

    @Test
    void failedPublicationSchedulesFirstRetryAfterOneMinute() {
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        NotificationEventClient client = mock(NotificationEventClient.class);
        NotificationOutboxEvent event = event(0);
        when(repository.findDue(20, 5)).thenReturn(List.of(event));
        when(client.publish(event.request())).thenReturn(Optional.empty());

        worker(repository, client, 5, true).runOnce();

        verify(repository).markFailed("ntf_test", "NOTIFICATION_PUBLISH_FAILED", NOW.plusSeconds(60));
    }

    @Test
    void exhaustedEventRemainsFailedWithoutAnotherSchedule() {
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        NotificationEventClient client = mock(NotificationEventClient.class);
        NotificationOutboxEvent event = event(4);
        when(repository.findDue(20, 5)).thenReturn(List.of(event));
        when(client.publish(event.request())).thenReturn(Optional.empty());

        worker(repository, client, 5, true).runOnce();

        verify(repository).markFailed("ntf_test", "NOTIFICATION_PUBLISH_FAILED", null);
    }

    @Test
    void enabledOutboxRejectsDisabledNotificationClient() {
        assertThatThrownBy(() -> worker(
                        mock(NotificationOutboxRepository.class),
                        mock(NotificationEventClient.class),
                        5,
                        false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须同时启用 Notification 客户端");
    }

    private NotificationOutboxWorker worker(
            NotificationOutboxRepository repository,
            NotificationEventClient client,
            int maxAttempts,
            boolean notificationEnabled) {
        return new NotificationOutboxWorker(
                repository,
                client,
                new NotificationOutboxRetryPolicy(),
                20,
                maxAttempts,
                notificationEnabled,
                CLOCK);
    }

    private NotificationOutboxEvent event(int attemptCount) {
        return new NotificationOutboxEvent("ntf_test", request(), attemptCount);
    }

    private NotificationEventRequest request() {
        return new NotificationEventRequest(
                "ntf_test",
                "trace_test",
                "smartcs-workbench",
                "APPROVAL_APPROVED",
                "u1001",
                "s_test",
                "wo_test",
                "agent001",
                "USER_SESSION",
                "审批通过通知",
                "审批已通过",
                Map.of("decision", "APPROVED"),
                NOW);
    }
}
