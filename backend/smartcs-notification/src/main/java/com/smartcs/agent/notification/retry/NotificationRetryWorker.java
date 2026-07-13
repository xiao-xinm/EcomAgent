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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 支持数据库租约的通知首次投递与失败重试 worker。 */
@Component
@ConditionalOnProperty(name = "smartcs.notification.retry.enabled", havingValue = "true")
public class NotificationRetryWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationRetryWorker.class);

    private final NotificationRetryRepository repository;
    private final Map<String, NotificationDeliveryChannel> channels;
    private final NotificationRetryPolicy retryPolicy;
    private final int batchSize;
    private final int maxAttempts;
    private final long leaseDurationMs;
    private final String workerId;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    public NotificationRetryWorker(
            NotificationRetryRepository repository,
            ObjectProvider<NotificationDeliveryChannel> channelProvider,
            @Value("${smartcs.notification.retry.batch-size:20}") int batchSize,
            @Value("${smartcs.notification.retry.max-attempts:5}") int maxAttempts,
            @Value("${smartcs.notification.retry.lease-duration-ms:120000}") long leaseDurationMs,
            @Value("${smartcs.notification.retry.worker-id:}") String configuredWorkerId) {
        this(
                repository,
                channelProvider.orderedStream().toList(),
                new NotificationRetryPolicy(),
                batchSize,
                maxAttempts,
                leaseDurationMs,
                resolveWorkerId(configuredWorkerId),
                Clock.systemUTC());
    }

    NotificationRetryWorker(
            NotificationRetryRepository repository,
            List<NotificationDeliveryChannel> channels,
            NotificationRetryPolicy retryPolicy,
            int batchSize,
            int maxAttempts,
            long leaseDurationMs,
            String workerId,
            Clock clock) {
        if (batchSize <= 0 || maxAttempts <= 0 || leaseDurationMs < 1000) {
            throw new IllegalArgumentException("batchSize、maxAttempts 必须大于 0，leaseDurationMs 不能小于 1000");
        }
        if (workerId == null || workerId.isBlank() || workerId.length() > 64) {
            throw new IllegalArgumentException("workerId 必须为 1 至 64 个字符");
        }
        this.repository = repository;
        this.channels = indexChannels(channels);
        this.retryPolicy = retryPolicy;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.leaseDurationMs = leaseDurationMs;
        this.workerId = workerId;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${smartcs.notification.retry.fixed-delay-ms:30000}")
    public void runOnce() {
        if (!running.compareAndSet(false, true)) {
            LOGGER.debug("Notification retry skipped because previous batch is still running");
            return;
        }
        try {
            List<NotificationRetryEvent> events =
                    repository.claimDue(workerId, batchSize, maxAttempts, leaseDurationMs);
            if (!events.isEmpty()) {
                LOGGER.info(
                        "Notification retry batch started workerId={} eventCount={} leaseDurationMs={}",
                        LogFields.value(workerId),
                        events.size(),
                        leaseDurationMs);
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
            int updated = repository.markDelivered(event.eventId(), workerId);
            if (updated != 1) {
                LOGGER.warn(
                        "Notification delivery result ignored because lease ownership changed eventId={} workerId={}",
                        LogFields.value(event.eventId()),
                        LogFields.value(workerId));
                return;
            }
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
        int updated = repository.markFailed(event.eventId(), workerId, message, decision.nextRetryAt());
        if (updated != 1) {
            LOGGER.warn(
                    "Notification failure result ignored because lease ownership changed eventId={} workerId={}",
                    LogFields.value(event.eventId()),
                    LogFields.value(workerId));
            return;
        }
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
        if (channelList.isEmpty()) {
            throw new IllegalArgumentException("至少需要注册一个通知投递通道");
        }
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

    private static String resolveWorkerId(String configuredWorkerId) {
        if (configuredWorkerId != null && !configuredWorkerId.isBlank()) {
            return configuredWorkerId.trim();
        }
        return "notification-" + UUID.randomUUID();
    }
}
