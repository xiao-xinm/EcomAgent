package com.smartcs.agent.workbench.notification.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcs.agent.workbench.notification.NotificationEventDtos.NotificationEventRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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

    public List<NotificationOutboxEvent> findDue(int batchSize, int maxAttempts) {
        if (batchSize <= 0 || maxAttempts <= 0) {
            throw new IllegalArgumentException("batchSize 和 maxAttempts 必须大于 0");
        }
        return jdbcTemplate.query(
                """
                SELECT event_id, event_payload, attempt_count
                FROM workbench_notification_outbox
                WHERE attempt_count < ?
                  AND (
                    status = 'PENDING'
                    OR (status = 'FAILED' AND (next_attempt_at IS NULL OR next_attempt_at <= NOW(3)))
                  )
                ORDER BY CASE status WHEN 'PENDING' THEN 0 ELSE 1 END,
                         COALESCE(next_attempt_at, created_at), created_at, event_id
                LIMIT ?
                """,
                (rs, rowNum) -> new NotificationOutboxEvent(
                        rs.getString("event_id"),
                        readRequest(rs.getString("event_payload")),
                        rs.getInt("attempt_count")),
                maxAttempts,
                batchSize);
    }

    public int markSent(String eventId) {
        return jdbcTemplate.update(
                """
                UPDATE workbench_notification_outbox
                SET status = 'SENT',
                    last_error = NULL,
                    next_attempt_at = NULL,
                    sent_at = NOW(3),
                    updated_at = NOW(3)
                WHERE event_id = ?
                  AND status IN ('PENDING', 'FAILED')
                """,
                eventId);
    }

    public int markFailed(String eventId, String errorMessage, Instant nextAttemptAt) {
        return jdbcTemplate.update(
                """
                UPDATE workbench_notification_outbox
                SET status = 'FAILED',
                    attempt_count = attempt_count + 1,
                    last_error = ?,
                    next_attempt_at = ?,
                    sent_at = NULL,
                    updated_at = NOW(3)
                WHERE event_id = ?
                  AND status IN ('PENDING', 'FAILED')
                """,
                normalizeError(errorMessage),
                nextAttemptAt == null ? null : Timestamp.from(nextAttemptAt),
                eventId);
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
