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
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public List<NotificationRetryEvent> claimDue(
            String workerId,
            int batchSize,
            int maxAttempts,
            long leaseDurationMs) {
        validateClaimArguments(workerId, batchSize, maxAttempts, leaseDurationMs);
        List<NotificationRetryEvent> events = jdbcTemplate.query(
                """
                SELECT event_id, trace_id, event_type, recipient_user_id, session_id,
                       ticket_id, operator_id, channel, title, content, payload,
                       retry_count, occurred_at
                FROM notification_event
                WHERE retry_count < ?
                  AND (delivery_lease_until IS NULL OR delivery_lease_until <= NOW(3))
                  AND (
                    status = 'ACCEPTED'
                    OR (status = 'FAILED' AND (next_retry_at IS NULL OR next_retry_at <= NOW(3)))
                  )
                ORDER BY CASE status WHEN 'ACCEPTED' THEN 0 ELSE 1 END,
                         COALESCE(next_retry_at, accepted_at), accepted_at, event_id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """,
                (rs, rowNum) -> mapEvent(rs),
                maxAttempts,
                batchSize);
        long leaseDurationMicros = Math.multiplyExact(leaseDurationMs, 1000L);
        for (NotificationRetryEvent event : events) {
            int claimed = jdbcTemplate.update(
                    """
                    UPDATE notification_event
                    SET delivery_owner = ?,
                        delivery_lease_until = TIMESTAMPADD(MICROSECOND, ?, NOW(3)),
                        updated_at = NOW(3)
                    WHERE event_id = ?
                      AND status IN ('ACCEPTED', 'FAILED')
                      AND (delivery_lease_until IS NULL OR delivery_lease_until <= NOW(3))
                    """,
                    workerId,
                    leaseDurationMicros,
                    event.eventId());
            if (claimed != 1) {
                throw new IllegalStateException("通知事件租约领取失败: " + event.eventId());
            }
        }
        return events;
    }

    public int markDelivered(String eventId, String workerId) {
        return jdbcTemplate.update(
                """
                UPDATE notification_event
                SET status = 'DELIVERED',
                    last_error = NULL,
                    next_retry_at = NULL,
                    delivered_at = NOW(3),
                    delivery_owner = NULL,
                    delivery_lease_until = NULL,
                    updated_at = NOW(3)
                WHERE event_id = ?
                  AND status IN ('ACCEPTED', 'FAILED')
                  AND delivery_owner = ?
                """,
                eventId,
                workerId);
    }

    public int markFailed(String eventId, String workerId, String errorMessage, Instant nextRetryAt) {
        return jdbcTemplate.update(
                """
                UPDATE notification_event
                SET status = 'FAILED',
                    retry_count = retry_count + 1,
                    last_error = ?,
                    next_retry_at = ?,
                    delivered_at = NULL,
                    delivery_owner = NULL,
                    delivery_lease_until = NULL,
                    updated_at = NOW(3)
                WHERE event_id = ?
                  AND status IN ('ACCEPTED', 'FAILED')
                  AND delivery_owner = ?
                """,
                normalizeError(errorMessage),
                nextRetryAt == null ? null : Timestamp.from(nextRetryAt),
                eventId,
                workerId);
    }

    public NotificationDeliverySummary summarize(int maxAttempts) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts 必须大于 0");
        }
        return jdbcTemplate.queryForObject(
                """
                SELECT
                    COALESCE(SUM(status = 'ACCEPTED'), 0) AS accepted_count,
                    COALESCE(SUM(status = 'FAILED' AND retry_count < ?), 0) AS retryable_failed_count,
                    COALESCE(SUM(status = 'FAILED' AND retry_count >= ?), 0) AS exhausted_failed_count,
                    COALESCE(SUM(status = 'DELIVERED'), 0) AS delivered_count,
                    COALESCE(SUM(
                        retry_count < ?
                        AND (delivery_lease_until IS NULL OR delivery_lease_until <= NOW(3))
                        AND (
                            status = 'ACCEPTED'
                            OR (status = 'FAILED' AND (next_retry_at IS NULL OR next_retry_at <= NOW(3)))
                        )
                    ), 0) AS due_count,
                    COALESCE(SUM(
                        status IN ('ACCEPTED', 'FAILED')
                        AND delivery_lease_until > NOW(3)
                    ), 0) AS leased_count,
                    MIN(CASE
                        WHEN retry_count < ?
                         AND (delivery_lease_until IS NULL OR delivery_lease_until <= NOW(3))
                         AND (
                            status = 'ACCEPTED'
                            OR (status = 'FAILED' AND (next_retry_at IS NULL OR next_retry_at <= NOW(3)))
                         )
                        THEN COALESCE(next_retry_at, accepted_at)
                    END) AS oldest_due_at
                FROM notification_event
                """,
                (rs, rowNum) -> new NotificationDeliverySummary(
                        true,
                        rs.getLong("accepted_count"),
                        rs.getLong("retryable_failed_count"),
                        rs.getLong("exhausted_failed_count"),
                        rs.getLong("delivered_count"),
                        rs.getLong("due_count"),
                        rs.getLong("leased_count"),
                        toInstant(rs.getTimestamp("oldest_due_at"))),
                maxAttempts,
                maxAttempts,
                maxAttempts,
                maxAttempts);
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
