package com.smartcs.agent.notification.retry;

import com.smartcs.agent.common.observability.LogFields;
import com.smartcs.agent.notification.delivery.NotificationDeliveryChannel;
import com.smartcs.agent.notification.retry.NotificationRetryPolicy.RetryDecision;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 单实例通知首次投递与失败重试 worker。 */
@Component
@ConditionalOnProperty(name = "smartcs.notification.retry.enabled", havingValue = "true")
public class NotificationRetryWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationRetryWorker.class);

    private final NotificationRetryRepository repository;
    private final Map<String, NotificationDeliveryChannel> channels;
    private final NotificationRetryPolicy retryPolicy;
    private final int batchSize;
    private final int maxAttempts;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    public NotificationRetryWorker(
            NotificationRetryRepository repository,
            ObjectProvider<NotificationDeliveryChannel> channelProvider,
            @Value("${smartcs.notification.retry.batch-size:20}") int batchSize,
            @Value("${smartcs.notification.retry.max-attempts:5}") int maxAttempts) {
        this(
                repository,
                channelProvider.orderedStream().toList(),
                new NotificationRetryPolicy(),
                batchSize,
                maxAttempts,
                Clock.systemUTC());
    }

    NotificationRetryWorker(
            NotificationRetryRepository repository,
            List<NotificationDeliveryChannel> channels,
            NotificationRetryPolicy retryPolicy,
            int batchSize,
            int maxAttempts,
            Clock clock) {
        if (batchSize <= 0 || maxAttempts <= 0) {
            throw new IllegalArgumentException("batchSize 和 maxAttempts 必须大于 0");
        }
        this.repository = repository;
        this.channels = indexChannels(channels);
        this.retryPolicy = retryPolicy;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${smartcs.notification.retry.fixed-delay-ms:30000}")
    public void runOnce() {
        if (!running.compareAndSet(false, true)) {
            LOGGER.debug("Notification retry skipped because previous batch is still running");
            return;
        }
        try {
            List<NotificationRetryEvent> events = repository.findDue(batchSize, maxAttempts);
            if (!events.isEmpty()) {
                LOGGER.info("Notification retry batch started eventCount={}", events.size());
            }
            events.forEach(this::deliver);
        } finally {
            running.set(false);
        }
    }

    private void deliver(NotificationRetryEvent event) {
        NotificationDeliveryChannel channel = channels.get(normalizeChannel(event.channel()));
        if (channel == null) {
            recordFailure(event, "UNSUPPORTED_CHANNEL: " + event.channel());
            return;
        }
        try {
            channel.deliver(event.toCommand());
            repository.markDelivered(event.eventId());
            LOGGER.info(
                    "Notification delivered eventId={} channel={} attempt={} traceId={} ticketId={} userId={}",
                    LogFields.value(event.eventId()),
                    LogFields.value(event.channel()),
                    event.retryCount() + 1,
                    LogFields.value(event.traceId()),
                    LogFields.value(event.ticketId()),
                    LogFields.value(event.recipientUserId()));
        } catch (RuntimeException exception) {
            recordFailure(event, safeMessage(exception));
        }
    }

    private void recordFailure(NotificationRetryEvent event, String message) {
        RetryDecision decision = retryPolicy.afterFailure(
                event.retryCount(),
                maxAttempts,
                Instant.now(clock));
        repository.markFailed(event.eventId(), message, decision.nextRetryAt());
        LOGGER.warn(
                "Notification delivery failed eventId={} channel={} attempt={} exhausted={} nextRetryAt={} reason={}",
                LogFields.value(event.eventId()),
                LogFields.value(event.channel()),
                decision.failedAttempts(),
                decision.exhausted(),
                LogFields.value(decision.nextRetryAt()),
                message);
    }

    private Map<String, NotificationDeliveryChannel> indexChannels(
            List<NotificationDeliveryChannel> channelList) {
        Map<String, NotificationDeliveryChannel> indexed = new LinkedHashMap<>();
        for (NotificationDeliveryChannel channel : channelList) {
            String name = normalizeChannel(channel.channel());
            if (name.isBlank()) {
                throw new IllegalArgumentException("通知通道名称不能为空");
            }
            if (indexed.putIfAbsent(name, channel) != null) {
                throw new IllegalArgumentException("存在重复通知通道：" + name);
            }
        }
        return Map.copyOf(indexed);
    }

    private String normalizeChannel(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
