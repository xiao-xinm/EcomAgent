package com.smartcs.agent.notification.retry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** notification_event 的待投递查询和状态回写仓储。 */
@Repository
public class NotificationRetryRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationRetryRepository.class);
    private static final int MAX_ERROR_LENGTH = 1000;
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public NotificationRetryRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<NotificationRetryEvent> findDue(int batchSize, int maxAttempts) {
        if (batchSize <= 0 || maxAttempts <= 0) {
            throw new IllegalArgumentException("batchSize 和 maxAttempts 必须大于 0");
        }
        return jdbcTemplate.query(
                """
                SELECT event_id, trace_id, event_type, recipient_user_id, session_id,
                       ticket_id, operator_id, channel, title, content, payload,
                       retry_count, occurred_at
                FROM notification_event
                WHERE retry_count < ?
                  AND (
                    status = 'ACCEPTED'
                    OR (status = 'FAILED' AND (next_retry_at IS NULL OR next_retry_at <= NOW(3)))
                  )
                ORDER BY CASE status WHEN 'ACCEPTED' THEN 0 ELSE 1 END,
                         COALESCE(next_retry_at, accepted_at), accepted_at, event_id
                LIMIT ?
                """,
                (rs, rowNum) -> mapEvent(rs),
                maxAttempts,
                batchSize);
    }

    public int markDelivered(String eventId) {
        return jdbcTemplate.update(
                """
                UPDATE notification_event
                SET status = 'DELIVERED',
                    last_error = NULL,
                    next_retry_at = NULL,
                    delivered_at = NOW(3),
                    updated_at = NOW(3)
                WHERE event_id = ?
                  AND status IN ('ACCEPTED', 'FAILED')
                """,
                eventId);
    }

    public int markFailed(String eventId, String errorMessage, Instant nextRetryAt) {
        return jdbcTemplate.update(
                """
                UPDATE notification_event
                SET status = 'FAILED',
                    retry_count = retry_count + 1,
                    last_error = ?,
                    next_retry_at = ?,
                    delivered_at = NULL,
                    updated_at = NOW(3)
                WHERE event_id = ?
                  AND status IN ('ACCEPTED', 'FAILED')
                """,
                normalizeError(errorMessage),
                nextRetryAt == null ? null : Timestamp.from(nextRetryAt),
                eventId);
    }

    private NotificationRetryEvent mapEvent(ResultSet rs) throws SQLException {
        return new NotificationRetryEvent(
                rs.getString("event_id"),
                rs.getString("trace_id"),
                rs.getString("event_type"),
                rs.getString("recipient_user_id"),
                rs.getString("session_id"),
                rs.getString("ticket_id"),
                rs.getString("operator_id"),
                rs.getString("channel"),
                rs.getString("title"),
                rs.getString("content"),
                readPayload(rs.getString("payload")),
                rs.getInt("retry_count"),
                toInstant(rs.getTimestamp("occurred_at")));
    }

    private Map<String, Object> readPayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, PAYLOAD_TYPE);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Notification retry payload 解析失败，使用空 payload");
            return Map.of();
        }
    }

    private String normalizeError(String message) {
        String normalized = message == null || message.isBlank() ? "UNKNOWN_ERROR" : message.trim();
        return normalized.length() <= MAX_ERROR_LENGTH
                ? normalized
                : normalized.substring(0, MAX_ERROR_LENGTH);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
