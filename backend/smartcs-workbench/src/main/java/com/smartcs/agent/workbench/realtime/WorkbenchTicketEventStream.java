package com.smartcs.agent.workbench.realtime;

import com.smartcs.agent.common.auth.AuthenticatedPrincipal;
import com.smartcs.agent.common.observability.LogFields;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 管理坐席 SSE 连接，并将共享 MySQL 中的增量变化广播给当前实例的订阅者。 */
@Component
@ConditionalOnProperty(name = "smartcs.realtime.ticket-events.enabled", havingValue = "true")
public class WorkbenchTicketEventStream {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkbenchTicketEventStream.class);

    private final WorkbenchTicketChangeRepository repository;
    private final int batchSize;
    private final long heartbeatIntervalMs;
    private final long emitterTimeoutMs;
    private final Clock clock;
    private final Function<Long, SseEmitter> emitterFactory;
    private final Map<String, SseEmitter> subscribers = new ConcurrentHashMap<>();
    private final AtomicBoolean polling = new AtomicBoolean(false);
    private final Object cursorMonitor = new Object();

    private TicketChangeCursor workOrderCursor;
    private TicketChangeCursor auditCursor;
    private Instant lastHeartbeatAt;

    @Autowired
    public WorkbenchTicketEventStream(
            WorkbenchTicketChangeRepository repository,
            @Value("${smartcs.realtime.ticket-events.batch-size:100}") int batchSize,
            @Value("${smartcs.realtime.ticket-events.heartbeat-interval-ms:15000}") long heartbeatIntervalMs,
            @Value("${smartcs.realtime.ticket-events.emitter-timeout-ms:1800000}") long emitterTimeoutMs) {
        this(repository, batchSize, heartbeatIntervalMs, emitterTimeoutMs, Clock.systemUTC(), SseEmitter::new);
    }

    WorkbenchTicketEventStream(
            WorkbenchTicketChangeRepository repository,
            int batchSize,
            long heartbeatIntervalMs,
            long emitterTimeoutMs,
            Clock clock,
            Function<Long, SseEmitter> emitterFactory) {
        if (batchSize <= 0 || batchSize > 1000) {
            throw new IllegalArgumentException("ticket SSE batchSize 必须在 1 至 1000 之间");
        }
        if (heartbeatIntervalMs < 1000 || emitterTimeoutMs < heartbeatIntervalMs) {
            throw new IllegalArgumentException("ticket SSE 心跳和连接超时配置无效");
        }
        this.repository = repository;
        this.batchSize = batchSize;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.emitterTimeoutMs = emitterTimeoutMs;
        this.clock = clock;
        this.emitterFactory = emitterFactory;
    }

    public SseEmitter open(AuthenticatedPrincipal principal) {
        String subscriberId = UUID.randomUUID().toString();
        SseEmitter emitter = emitterFactory.apply(emitterTimeoutMs);
        emitter.onCompletion(() -> remove(subscriberId));
        emitter.onTimeout(() -> remove(subscriberId));
        emitter.onError(error -> remove(subscriberId));

        synchronized (cursorMonitor) {
            if (subscribers.isEmpty()) {
                TicketChangeCursor initialCursor = repository.currentCursor();
                Instant now = Instant.now(clock);
                workOrderCursor = initialCursor;
                auditCursor = initialCursor;
                lastHeartbeatAt = now;
            }
            subscribers.put(subscriberId, emitter);
        }

        try {
            emitter.send(SseEmitter.event()
                    .name("stream.ready")
                    .data(Map.of(
                            "operatorId", principal.principalId(),
                            "connectedAt", Instant.now(clock).toString())));
            LOGGER.info(
                    "Workbench工单SSE连接建立 subscriberId={} operatorId={} subscriberCount={}",
                    LogFields.value(subscriberId),
                    LogFields.value(principal.principalId()),
                    subscribers.size());
        } catch (IOException | IllegalStateException exception) {
            remove(subscriberId);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    @Scheduled(fixedDelayString = "${smartcs.realtime.ticket-events.poll-interval-ms:2000}")
    public void pollOnce() {
        if (subscribers.isEmpty() || !polling.compareAndSet(false, true)) {
            return;
        }
        try {
            TicketChangeCursor currentWorkOrderCursor;
            TicketChangeCursor currentAuditCursor;
            synchronized (cursorMonitor) {
                currentWorkOrderCursor = workOrderCursor;
                currentAuditCursor = auditCursor;
            }
            if (currentWorkOrderCursor == null || currentAuditCursor == null) {
                return;
            }

            TicketChangeBatch workOrderBatch = repository.findWorkOrderChanges(currentWorkOrderCursor, batchSize);
            TicketChangeBatch auditBatch = repository.findAuditChanges(currentAuditCursor, batchSize);
            synchronized (cursorMonitor) {
                workOrderCursor = workOrderBatch.nextCursor();
                auditCursor = auditBatch.nextCursor();
            }

            mergeLatestByTicket(workOrderBatch.events(), auditBatch.events()).forEach(this::broadcastChange);
            sendHeartbeatWhenDue();
        } catch (RuntimeException exception) {
            LOGGER.warn("Workbench工单SSE增量扫描失败 subscriberCount={}", subscribers.size(), exception);
        } finally {
            polling.set(false);
        }
    }

    private List<TicketChangedEvent> mergeLatestByTicket(
            List<TicketChangedEvent> workOrderEvents,
            List<TicketChangedEvent> auditEvents) {
        Map<String, TicketChangedEvent> latest = new LinkedHashMap<>();
        java.util.stream.Stream.concat(workOrderEvents.stream(), auditEvents.stream())
                .sorted(Comparator.comparing(TicketChangedEvent::changedAt)
                        .thenComparing(TicketChangedEvent::eventId))
                .forEach(event -> latest.put(event.ticketId(), event));
        return List.copyOf(latest.values());
    }

    private void broadcastChange(TicketChangedEvent event) {
        broadcast(emitter -> emitter.send(SseEmitter.event()
                .id(event.eventId())
                .name("ticket.changed")
                .data(event)));
    }

    private void sendHeartbeatWhenDue() {
        Instant now = Instant.now(clock);
        synchronized (cursorMonitor) {
            if (lastHeartbeatAt != null
                    && Duration.between(lastHeartbeatAt, now).toMillis() < heartbeatIntervalMs) {
                return;
            }
            lastHeartbeatAt = now;
        }
        broadcast(emitter -> emitter.send(SseEmitter.event()
                .name("heartbeat")
                .data(Map.of("timestamp", now.toString()))));
    }

    private void broadcast(EmitterSendOperation operation) {
        subscribers.forEach((subscriberId, emitter) -> {
            try {
                operation.send(emitter);
            } catch (IOException | IllegalStateException exception) {
                LOGGER.debug(
                        "Workbench工单SSE发送失败 subscriberId={}",
                        LogFields.value(subscriberId),
                        exception);
                remove(subscriberId);
                emitter.completeWithError(exception);
            }
        });
    }

    private void remove(String subscriberId) {
        SseEmitter removed = subscribers.remove(subscriberId);
        if (removed == null) {
            return;
        }
        synchronized (cursorMonitor) {
            if (subscribers.isEmpty()) {
                workOrderCursor = null;
                auditCursor = null;
                lastHeartbeatAt = null;
            }
        }
        LOGGER.info(
                "Workbench工单SSE连接关闭 subscriberId={} subscriberCount={}",
                LogFields.value(subscriberId),
                subscribers.size());
    }

    @FunctionalInterface
    private interface EmitterSendOperation {
        void send(SseEmitter emitter) throws IOException;
    }
}
