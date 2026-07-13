package com.smartcs.agent.notification.delivery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcs.agent.common.observability.LogFields;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 将 Notification 的 USER_SESSION 事件幂等写入用户会话消息表。 */
@Component
@ConditionalOnProperty(
        name = "smartcs.notification.channel.user-session.enabled",
        havingValue = "true")
public class UserSessionNotificationDeliveryChannel implements NotificationDeliveryChannel {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserSessionNotificationDeliveryChannel.class);
    private static final String DIRECT = "DIRECT";
    private static final String NOTIFICATION = "NOTIFICATION";
    private static final Set<String> MESSAGE_ROLES = Set.of("SYSTEM", "HUMAN_AGENT");
    private static final Set<String> CONTRACT_FIELDS = Set.of(
            "messageRole",
            "userMessageDeliveryMode",
            "intent",
            "riskLevel",
            "routeDecision");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public UserSessionNotificationDeliveryChannel(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${smartcs.notification.retry.enabled:false}") boolean retryEnabled) {
        if (!retryEnabled) {
            throw new IllegalArgumentException("启用 USER_SESSION 通道时必须同时启用 Notification 重试 worker");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String channel() {
        return "USER_SESSION";
    }

    @Override
    public void deliver(NotificationDeliveryCommand command) {
        Map<String, Object> payload = command.payload() == null ? Map.of() : command.payload();
        String deliveryMode = text(payload.get("userMessageDeliveryMode")).toUpperCase(Locale.ROOT);

        // 历史事件和 DIRECT 事件已由 Workbench 写入 cs_message，仅确认成功，避免重复消息。
        if (deliveryMode.isBlank() || DIRECT.equals(deliveryMode)) {
            LOGGER.info(
                    "USER_SESSION delivery skipped for direct or legacy event eventId={} mode={} sessionId={}",
                    LogFields.value(command.eventId()),
                    LogFields.value(deliveryMode.isBlank() ? "LEGACY_DIRECT" : deliveryMode),
                    LogFields.value(command.sessionId()));
            return;
        }
        if (!NOTIFICATION.equals(deliveryMode)) {
            throw new IllegalArgumentException("不支持的用户消息交付模式: " + deliveryMode);
        }

        String eventId = required(command.eventId(), "eventId");
        String traceId = required(command.traceId(), "traceId");
        String sessionId = required(command.sessionId(), "sessionId");
        String userId = required(command.recipientUserId(), "recipientUserId");
        String content = required(command.content(), "content");
        String role = required(text(payload.get("messageRole")), "payload.messageRole")
                .toUpperCase(Locale.ROOT);
        if (!MESSAGE_ROLES.contains(role)) {
            throw new IllegalArgumentException("USER_SESSION 通道只允许 SYSTEM 或 HUMAN_AGENT 消息");
        }

        String messageId = stableMessageId(eventId);
        Map<String, Object> metadata = messageMetadata(command, payload);
        jdbcTemplate.update(
                """
                INSERT INTO cs_message (
                    message_id, trace_id, session_id, user_id, role, message_type,
                    content, intent, risk_level, route_decision, metadata
                ) VALUES (?, ?, ?, ?, ?, 'TEXT', ?, ?, ?, ?, CAST(? AS JSON))
                ON DUPLICATE KEY UPDATE message_id = VALUES(message_id)
                """,
                messageId,
                traceId,
                sessionId,
                userId,
                role,
                content,
                nullableText(payload.get("intent")),
                nullableText(payload.get("riskLevel")),
                nullableText(payload.get("routeDecision")),
                json(metadata));
        LOGGER.info(
                "USER_SESSION delivered eventId={} messageId={} traceId={} sessionId={} ticketId={} userId={}",
                LogFields.value(eventId),
                LogFields.value(messageId),
                LogFields.value(traceId),
                LogFields.value(sessionId),
                LogFields.value(command.ticketId()),
                LogFields.value(userId));
    }

    private Map<String, Object> messageMetadata(
            NotificationDeliveryCommand command,
            Map<String, Object> payload) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        payload.forEach((key, value) -> {
            if (!CONTRACT_FIELDS.contains(key) && value != null) {
                metadata.put(key, value);
            }
        });
        metadata.put("source", "notification");
        metadata.put("eventId", command.eventId());
        putIfPresent(metadata, "ticketId", command.ticketId());
        putIfPresent(metadata, "operatorId", command.operatorId());
        putIfPresent(metadata, "actionType", command.eventType());
        return metadata;
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private String stableMessageId(String eventId) {
        return "m_" + UUID.nameUUIDFromBytes(eventId.getBytes(StandardCharsets.UTF_8));
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }

    private String nullableText(Object value) {
        String normalized = text(value);
        return normalized.isBlank() ? null : normalized;
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("用户会话消息 metadata 序列化失败", exception);
        }
    }
}
