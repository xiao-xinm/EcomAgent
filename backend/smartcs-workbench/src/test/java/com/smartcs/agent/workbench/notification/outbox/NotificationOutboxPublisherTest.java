package com.smartcs.agent.workbench.notification.outbox;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.smartcs.agent.workbench.notification.NotificationEventClient;
import com.smartcs.agent.workbench.notification.NotificationEventDtos.NotificationEventRequest;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationOutboxPublisherTest {

    @Test
    void disabledOutboxPreservesDirectHttpPublication() {
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        NotificationEventClient client = mock(NotificationEventClient.class);
        NotificationOutboxPublisher publisher = new NotificationOutboxPublisher(repository, client, false);
        NotificationEventRequest request = request();

        publisher.publish(request);

        verify(client).publish(request);
        verifyNoInteractions(repository);
    }

    @Test
    void enabledOutboxEnqueuesInsideCallerTransaction() {
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        NotificationEventClient client = mock(NotificationEventClient.class);
        NotificationOutboxPublisher publisher = new NotificationOutboxPublisher(repository, client, true);
        NotificationEventRequest request = request();

        publisher.publish(request);

        verify(repository).enqueue(request);
        verifyNoInteractions(client);
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
                Instant.parse("2026-07-13T07:00:00Z"));
    }
}
