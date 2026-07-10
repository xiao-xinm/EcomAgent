package com.smartcs.agent.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcs.agent.common.dto.PageResult;
import com.smartcs.agent.common.enums.ErrorCode;
import com.smartcs.agent.common.exception.SmartCsException;
import com.smartcs.agent.common.observability.LogFields;
import com.smartcs.agent.notification.dto.NotificationEventDtos.NotificationDeliveryResult;
import com.smartcs.agent.notification.dto.NotificationEventDtos.NotificationDeliveryResultRequest;
import com.smartcs.agent.notification.dto.NotificationEventDtos.NotificationEventRequest;
import com.smartcs.agent.notification.dto.NotificationEventDtos.NotificationEventResult;
import com.smartcs.agent.notification.dto.NotificationEventDtos.NotificationEventView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 通知事件服务。
 *
 * <p>当前阶段只做 MySQL 可追踪事件，不引入 RocketMQ。后续如果需要短信、站内信、
 * 坐席提醒或消息队列，可基于 notification_event 表继续扩展投递状态和重试策略。
 */
@Service
public class NotificationEventService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationEventService.class);
    private static final String DEFAULT_CHANNEL = "USER_SESSION";
    private static final String STATUS_ACCEPTED = "ACCEPTED";
    private static final String STATUS_DELIVERED = "DELIVERED";
    private static final String STATUS_FAILED = "FAILED";
    private static final int MAX_ERROR_LENGTH = 1000;
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public NotificationEventService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public NotificationEventResult accept(NotificationEventRequest request) {
        String eventId = textOr(request.eventId(), "ntf_" + UUID.randomUUID());
        String channel = textOr(request.channel(), DEFAULT_CHANNEL);
        Instant acceptedAt = Instant.now();
        Instant occurredAt = request.occurredAt() == null ? acceptedAt : request.occurredAt();
        Map<String, Object> payload = request.payload() == null ? Map.of() : request.payload();
        jdbcTemplate.update(
                """
                INSERT INTO notification_event (
                    event_id, trace_id, source_service, event_type, recipient_user_id,
                    session_id, ticket_id, operator_id, channel, title, content, payload,
                    status, retry_count, last_error, next_retry_at, delivered_at,
                    occurred_at, accepted_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NULL, NULL, NULL, ?, ?, NOW(3), NOW(3))
                ON DUPLICATE KEY UPDATE
                    trace_id = VALUES(trace_id),
                    source_service = VALUES(source_service),
                    event_type = VALUES(event_type),
                    recipient_user_id = VALUES(recipient_user_id),
                    session_id = VALUES(session_id),
                    ticket_id = VALUES(ticket_id),
                    operator_id = VALUES(operator_id),
                    channel = VALUES(channel),
                    title = VALUES(title),
                    content = VALUES(content),
                    payload = VALUES(payload),
                    status = VALUES(status),
                    retry_count = 0,
                    last_error = NULL,
                    next_retry_at = NULL,
                    delivered_at = NULL,
                    occurred_at = VALUES(occurred_at),
                    accepted_at = VALUES(accepted_at),
                    updated_at = NOW(3)
                """,
                eventId,
                request.traceId(),
                request.sourceService(),
                request.eventType(),
                request.recipientUserId(),
                request.sessionId(),
                request.ticketId(),
                request.operatorId(),
                channel,
                request.title(),
                request.content(),
                toJson(payload),
                STATUS_ACCEPTED,
                timestamp(occurredAt),
                timestamp(acceptedAt));
        LOGGER.info(
                "Notification event persisted {} eventId={} eventType={} channel={} title={} contentLength={}",
                eventFields(request),
                LogFields.value(eventId),
                LogFields.value(request.eventType()),
                LogFields.value(channel),
                LogFields.value(request.title()),
                request.content() == null ? 0 : request.content().length());
        return new NotificationEventResult(eventId, STATUS_ACCEPTED, channel, acceptedAt);
    }

    public NotificationDeliveryResult recordDeliveryResult(
            String eventId,
            NotificationDeliveryResultRequest request) {
        String normalizedEventId = requireText(eventId, "通知事件ID不能为空");
        if (request == null) {
            throw new SmartCsException(ErrorCode.BAD_REQUEST, "通知投递结果不能为空");
        }
        String normalizedStatus = requireText(request.status(), "通知投递状态不能为空").toUpperCase(Locale.ROOT);
        if (STATUS_DELIVERED.equals(normalizedStatus)) {
            Instant deliveredAt = Instant.now();
            int updated = jdbcTemplate.update(
                    """
                    UPDATE notification_event
                    SET status = ?,
                        last_error = NULL,
                        next_retry_at = NULL,
                        delivered_at = ?,
                        updated_at = NOW(3)
                    WHERE event_id = ?
                    """,
                    STATUS_DELIVERED,
                    timestamp(deliveredAt),
                    normalizedEventId);
            ensureUpdated(updated, normalizedEventId);
        } else if (STATUS_FAILED.equals(normalizedStatus)) {
            String lastError = normalizeError(request.errorMessage());
            int updated = jdbcTemplate.update(
                    """
                    UPDATE notification_event
                    SET status = ?,
                        retry_count = retry_count + 1,
                        last_error = ?,
                        next_retry_at = ?,
                        delivered_at = NULL,
                        updated_at = NOW(3)
                    WHERE event_id = ?
                    """,
                    STATUS_FAILED,
                    lastError,
                    nullableTimestamp(request.nextRetryAt()),
                    normalizedEventId);
            ensureUpdated(updated, normalizedEventId);
        } else {
            throw new SmartCsException(ErrorCode.BAD_REQUEST, "不支持的通知投递状态：" + normalizedStatus);
        }
        NotificationDeliveryResult result = findDeliveryResult(normalizedEventId);
        LOGGER.info(
                "Notification delivery status changed eventId={} status={} retryCount={} nextRetryAt={} deliveredAt={}",
                LogFields.value(result.eventId()),
                LogFields.value(result.status()),
                result.retryCount(),
                LogFields.value(result.nextRetryAt()),
                LogFields.value(result.deliveredAt()));
        return result;
    }

    public PageResult<NotificationEventView> list(
            String eventType,
            String ticketId,
            String recipientUserId,
            String status,
            int pageNo,
            int pageSize) {
        int normalizedPageNo = Math.max(1, pageNo);
        int normalizedPageSize = Math.min(100, Math.max(1, pageSize));
        int offset = (normalizedPageNo - 1) * normalizedPageSize;
        List<Object> params = new ArrayList<>();
        String whereClause = buildWhereClause(eventType, ticketId, recipientUserId, status, params);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_event" + whereClause,
                Long.class,
                params.toArray());
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(normalizedPageSize);
        queryParams.add(offset);
        List<NotificationEventView> records = jdbcTemplate.query(
                """
                SELECT event_id, trace_id, source_service, event_type, recipient_user_id,
                       session_id, ticket_id, operator_id, channel, title, content, payload,
                       status, retry_count, last_error, next_retry_at, delivered_at,
                       occurred_at, accepted_at, created_at, updated_at
                FROM notification_event
                """
                        + whereClause
                        + """

                ORDER BY accepted_at DESC, event_id ASC
                LIMIT ? OFFSET ?
                """,
                (rs, rowNum) -> mapEvent(rs),
                queryParams.toArray());
        return new PageResult<>(records, total == null ? 0L : total, normalizedPageNo, normalizedPageSize);
    }

    private String buildWhereClause(
            String eventType,
            String ticketId,
            String recipientUserId,
            String status,
            List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (eventType != null && !eventType.isBlank()) {
            where.append(" AND event_type = ?");
            params.add(eventType.trim());
        }
        if (ticketId != null && !ticketId.isBlank()) {
            where.append(" AND ticket_id = ?");
            params.add(ticketId.trim());
        }
        if (recipientUserId != null && !recipientUserId.isBlank()) {
            where.append(" AND recipient_user_id = ?");
            params.add(recipientUserId.trim());
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            params.add(status.trim().toUpperCase(Locale.ROOT));
        }
        return where.toString();
    }

    private NotificationEventView mapEvent(ResultSet rs) throws SQLException {
        return new NotificationEventView(
                rs.getString("event_id"),
                rs.getString("trace_id"),
                rs.getString("source_service"),
                rs.getString("event_type"),
                rs.getString("recipient_user_id"),
                rs.getString("session_id"),
                rs.getString("ticket_id"),
                rs.getString("operator_id"),
                rs.getString("channel"),
                rs.getString("title"),
                rs.getString("content"),
                readPayload(rs.getString("payload")),
                rs.getString("status"),
                rs.getInt("retry_count"),
                rs.getString("last_error"),
                toInstant(rs.getTimestamp("next_retry_at")),
                toInstant(rs.getTimestamp("delivered_at")),
                toInstant(rs.getTimestamp("occurred_at")),
                toInstant(rs.getTimestamp("accepted_at")),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")));
    }

    private NotificationDeliveryResult findDeliveryResult(String eventId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT event_id, status, retry_count, last_error, next_retry_at, delivered_at, updated_at
                    FROM notification_event
                    WHERE event_id = ?
                    """,
                    (rs, rowNum) -> new NotificationDeliveryResult(
                            rs.getString("event_id"),
                            rs.getString("status"),
                            rs.getInt("retry_count"),
                            rs.getString("last_error"),
                            toInstant(rs.getTimestamp("next_retry_at")),
                            toInstant(rs.getTimestamp("delivered_at")),
                            toInstant(rs.getTimestamp("updated_at"))),
                    eventId);
        } catch (EmptyResultDataAccessException exception) {
            throw new SmartCsException(ErrorCode.NOT_FOUND, "通知事件不存在：" + eventId);
        }
    }

    private void ensureUpdated(int updated, String eventId) {
        if (updated <= 0) {
            throw new SmartCsException(ErrorCode.NOT_FOUND, "通知事件不存在：" + eventId);
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new SmartCsException(ErrorCode.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String normalizeError(String errorMessage) {
        String normalized = textOr(errorMessage, "UNKNOWN_ERROR").trim();
        if (normalized.length() > MAX_ERROR_LENGTH) {
            return normalized.substring(0, MAX_ERROR_LENGTH);
        }
        return normalized;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("通知事件 payload 序列化失败", exception);
        }
    }

    private Map<String, Object> readPayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, PAYLOAD_TYPE);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Notification payload JSON parse failed, fallback to empty payload. value={}", payloadJson);
            return Map.of();
        }
    }

    private String eventFields(NotificationEventRequest request) {
        return LogFields.keyValues(
                LogFields.TRACE_ID,
                request.traceId(),
                LogFields.TICKET_ID,
                request.ticketId(),
                LogFields.USER_ID,
                request.recipientUserId(),
                LogFields.SESSION_ID,
                request.sessionId(),
                LogFields.OPERATOR_ID,
                request.operatorId());
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private Timestamp nullableTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
