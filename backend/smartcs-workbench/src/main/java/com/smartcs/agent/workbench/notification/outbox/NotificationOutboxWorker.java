package com.smartcs.agent.workbench.notification.outbox;

import com.smartcs.agent.common.observability.LogFields;
import com.smartcs.agent.workbench.notification.NotificationEventClient;
import com.smartcs.agent.workbench.notification.outbox.NotificationOutboxRetryPolicy.RetryDecision;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 单实例 Workbench 通知 outbox 投递 worker。 */
@Component
@ConditionalOnProperty(name = "smartcs.notification.outbox.enabled", havingValue = "true")
public class NotificationOutboxWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationOutboxWorker.class);

    private final NotificationOutboxRepository repository;
    private final NotificationEventClient client;
    private final NotificationOutboxRetryPolicy retryPolicy;
    private final int batchSize;
    private final int maxAttempts;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    public NotificationOutboxWorker(
            NotificationOutboxRepository repository,
            NotificationEventClient client,
            @Value("${smartcs.notification.outbox.batch-size:20}") int batchSize,
            @Value("${smartcs.notification.outbox.max-attempts:5}") int maxAttempts,
            @Value("${smartcs.notification.enabled:true}") boolean notificationEnabled) {
        this(
                repository,
                client,
                new NotificationOutboxRetryPolicy(),
                batchSize,
                maxAttempts,
                notificationEnabled,
                Clock.systemUTC());
    }

    NotificationOutboxWorker(
            NotificationOutboxRepository repository,
            NotificationEventClient client,
            NotificationOutboxRetryPolicy retryPolicy,
            int batchSize,
            int maxAttempts,
            boolean notificationEnabled,
            Clock clock) {
        if (batchSize <= 0 || maxAttempts <= 0) {
            throw new IllegalArgumentException("batchSize 和 maxAttempts 必须大于 0");
        }
        if (!notificationEnabled) {
            throw new IllegalArgumentException("启用通知 outbox 时必须同时启用 Notification 客户端");
        }
        this.repository = repository;
        this.client = client;
        this.retryPolicy = retryPolicy;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${smartcs.notification.outbox.fixed-delay-ms:30000}")
    public void runOnce() {
        if (!running.compareAndSet(false, true)) {
            LOGGER.debug("Notification outbox skipped because previous batch is still running");
            return;
        }
        try {
            List<NotificationOutboxEvent> events = repository.findDue(batchSize, maxAttempts);
            if (!events.isEmpty()) {
                LOGGER.info("Notification outbox batch started eventCount={}", events.size());
            }
            events.forEach(this::deliver);
        } finally {
            running.set(false);
        }
    }

    private void deliver(NotificationOutboxEvent event) {
        try {
            if (client.publish(event.request()).isPresent()) {
                repository.markSent(event.eventId());
                LOGGER.info(
                        "Notification outbox delivered eventId={} eventType={} attempt={} traceId={} ticketId={}",
                        LogFields.value(event.eventId()),
                        LogFields.value(event.request().eventType()),
                        event.attemptCount() + 1,
                        LogFields.value(event.request().traceId()),
                        LogFields.value(event.request().ticketId()));
                return;
            }
            recordFailure(event, "NOTIFICATION_PUBLISH_FAILED");
        } catch (RuntimeException exception) {
            recordFailure(event, safeMessage(exception));
        }
    }

    private void recordFailure(NotificationOutboxEvent event, String message) {
        RetryDecision decision = retryPolicy.afterFailure(
                event.attemptCount(),
                maxAttempts,
                Instant.now(clock));
        repository.markFailed(event.eventId(), message, decision.nextAttemptAt());
        LOGGER.warn(
                "Notification outbox delivery failed eventId={} attempt={} exhausted={} nextAttemptAt={} reason={}",
                LogFields.value(event.eventId()),
                decision.failedAttempts(),
                decision.exhausted(),
                LogFields.value(decision.nextAttemptAt()),
                LogFields.value(message));
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
