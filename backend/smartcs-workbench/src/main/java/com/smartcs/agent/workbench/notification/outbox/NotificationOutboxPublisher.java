package com.smartcs.agent.workbench.notification.outbox;

import com.smartcs.agent.common.observability.LogFields;
import com.smartcs.agent.workbench.notification.NotificationEventClient;
import com.smartcs.agent.workbench.notification.NotificationEventDtos.NotificationEventRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 在兼容直接 HTTP 与事务 outbox 两种模式之间切换的统一发布边界。 */
@Service
public class NotificationOutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationOutboxPublisher.class);

    private final NotificationOutboxRepository repository;
    private final NotificationEventClient client;
    private final boolean outboxEnabled;

    public NotificationOutboxPublisher(
            NotificationOutboxRepository repository,
            NotificationEventClient client,
            @Value("${smartcs.notification.outbox.enabled:false}") boolean outboxEnabled) {
        this.repository = repository;
        this.client = client;
        this.outboxEnabled = outboxEnabled;
    }

    public void publish(NotificationEventRequest request) {
        if (!outboxEnabled) {
            client.publish(request);
            return;
        }
        repository.enqueue(request);
        LOGGER.info(
                "Notification event enqueued eventId={} eventType={} traceId={} ticketId={}",
                LogFields.value(request.eventId()),
                LogFields.value(request.eventType()),
                LogFields.value(request.traceId()),
                LogFields.value(request.ticketId()));
    }
}
