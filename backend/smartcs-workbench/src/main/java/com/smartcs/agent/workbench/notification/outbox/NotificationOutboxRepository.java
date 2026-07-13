package com.smartcs.agent.workbench.notification.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcs.agent.workbench.notification.NotificationEventDtos.NotificationEventRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Workbench 通知事务 outbox 的入队、到期查询和状态回写仓储。 */
@Repository
public class NotificationOutboxRepository {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public NotificationOutboxRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public int enqueue(NotificationEventRequest request) {
        return jdbcTemplate.update(
                """
                INSERT INTO workbench_notification_outbox (
                    event_id, trace_id, ticket_id, event_type, channel, event_payload
                ) VALUES (?, ?, ?, ?, ?, CAST(? AS JSON))
                ON DUPLICATE KEY UPDATE event_id = VALUES(event_id)
                """,
                request.eventId(),
                request.traceId(),
                request.ticketId(),
                request.eventType(),
                request.channel(),
                writeRequest(request));
    }

    @Transactional
    public List<NotificationOutboxEvent> claimDue(
            String workerId,
            int batchSize,
            int maxAttempts,
            long leaseDurationMs) {
        validateClaimArguments(workerId, batchSize, maxAttempts, leaseDurationMs);
        List<NotificationOutboxEvent> events = jdbcTemplate.query(
                """
                SELECT event_id, event_payload, attempt_count
                FROM workbench_notification_outbox
                WHERE attempt_count < ?
                  AND (delivery_lease_until IS NULL OR delivery_lease_until <= NOW(3))
                  AND (
                    status = 'PENDING'
                    OR (status = 'FAILED' AND (next_attempt_at IS NULL OR next_attempt_at <= NOW(3)))
                  )
                ORDER BY CASE status WHEN 'PENDING' THEN 0 ELSE 1 END,
                         COALESCE(next_attempt_at, created_at), created_at, event_id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """,
                (rs, rowNum) -> new NotificationOutboxEvent(
                        rs.getString("event_id"),
                        readRequest(rs.getString("event_payload")),
                        rs.getInt("attempt_count")),
                maxAttempts,
                batchSize);
        long leaseDurationMicros = Math.multiplyExact(leaseDurationMs, 1000L);
        for (NotificationOutboxEvent event : events) {
            int claimed = jdbcTemplate.update(
                    """
                    UPDATE workbench_notification_outbox
                    SET delivery_owner = ?,
                        delivery_lease_until = TIMESTAMPADD(MICROSECOND, ?, NOW(3)),
                        updated_at = NOW(3)
                    WHERE event_id = ?
                      AND status IN ('PENDING', 'FAILED')
                      AND (delivery_lease_until IS NULL OR delivery_lease_until <= NOW(3))
                    """,
                    workerId,
                    leaseDurationMicros,
                    event.eventId());
            if (claimed != 1) {
                throw new IllegalStateException("通知 outbox 事件租约领取失败: " + event.eventId());
            }
        }
        return events;
    }

    public int markSent(String eventId, String workerId) {
        return jdbcTemplate.update(
                """
                UPDATE workbench_notification_outbox
                SET status = 'SENT',
                    last_error = NULL,
                    next_attempt_at = NULL,
                    sent_at = NOW(3),
                    delivery_owner = NULL,
                    delivery_lease_until = NULL,
                    updated_at = NOW(3)
                WHERE event_id = ?
                  AND status IN ('PENDING', 'FAILED')
                  AND delivery_owner = ?
                """,
                eventId,
                workerId);
    }

    public int markFailed(String eventId, String workerId, String errorMessage, Instant nextAttemptAt) {
        return jdbcTemplate.update(
                """
                UPDATE workbench_notification_outbox
                SET status = 'FAILED',
                    attempt_count = attempt_count + 1,
                    last_error = ?,
                    next_attempt_at = ?,
                    sent_at = NULL,
                    delivery_owner = NULL,
                    delivery_lease_until = NULL,
                    updated_at = NOW(3)
                WHERE event_id = ?
                  AND status IN ('PENDING', 'FAILED')
                  AND delivery_owner = ?
                """,
                normalizeError(errorMessage),
                nextAttemptAt == null ? null : Timestamp.from(nextAttemptAt),
                eventId,
                workerId);
    }

    private void validateClaimArguments(
            String workerId,
            int batchSize,
            int maxAttempts,
            long leaseDurationMs) {
        if (workerId == null || workerId.isBlank() || workerId.length() > 64) {
            throw new IllegalArgumentException("workerId 必须为 1 至 64 个字符");
        }
        if (batchSize <= 0 || maxAttempts <= 0) {
            throw new IllegalArgumentException("batchSize 和 maxAttempts 必须大于 0");
        }
        if (leaseDurationMs < 1000) {
            throw new IllegalArgumentException("leaseDurationMs 不能小于 1000");
        }
        Math.multiplyExact(leaseDurationMs, 1000L);
    }

    private String writeRequest(NotificationEventRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("通知 outbox 事件序列化失败: " + request.eventId(), exception);
        }
    }

    private NotificationEventRequest readRequest(String requestJson) {
        try {
            return objectMapper.readValue(requestJson, NotificationEventRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("通知 outbox 事件反序列化失败", exception);
        }
    }

    private String normalizeError(String message) {
        String normalized = message == null || message.isBlank() ? "UNKNOWN_ERROR" : message.trim();
        return normalized.length() <= MAX_ERROR_LENGTH
                ? normalized
                : normalized.substring(0, MAX_ERROR_LENGTH);
    }
}
