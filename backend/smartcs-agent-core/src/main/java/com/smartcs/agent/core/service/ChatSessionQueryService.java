package com.smartcs.agent.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcs.agent.common.domain.ChatMessageView;
import com.smartcs.agent.common.domain.ChatSessionView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 用户端会话查询服务：用于 H5 刷新或轮询会话状态和消息。
 */
@Service
public class ChatSessionQueryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatSessionQueryService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ChatSessionQueryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<ChatSessionView> findSession(String sessionId) {
        List<ChatSessionView> rows = jdbcTemplate.query(
                """
                SELECT session_id, trace_id, user_id, channel, status, dialog_state, current_intent,
                       slots, context_snapshot, last_message_at, created_at, updated_at, closed_at
                FROM cs_session
                WHERE session_id = ?
                """,
                this::mapSession,
                sessionId);
        Optional<ChatSessionView> session = rows.stream().findFirst();
        LOGGER.info("查询用户端会话 sessionId={} found={}", sessionId, session.isPresent());
        return session;
    }

    public List<ChatMessageView> listMessages(String sessionId, int limit) {
        int normalizedLimit = Math.min(Math.max(limit, 1), 200);
        List<ChatMessageView> messages = jdbcTemplate.query(
                """
                SELECT message_id, trace_id, session_id, user_id, role, message_type, content,
                       attachments, quick_actions, intent, risk_level, route_decision, metadata, created_at
                FROM cs_message
                WHERE session_id = ?
                ORDER BY created_at ASC
                LIMIT ?
                """,
                this::mapMessage,
                sessionId,
                normalizedLimit);
        LOGGER.info("查询用户端会话消息 sessionId={} limit={} count={}", sessionId, normalizedLimit, messages.size());
        return messages;
    }

    private ChatSessionView mapSession(ResultSet rs, int rowNum) throws SQLException {
        return new ChatSessionView(
                rs.getString("session_id"),
                rs.getString("trace_id"),
                rs.getString("user_id"),
                rs.getString("channel"),
                rs.getString("status"),
                rs.getString("dialog_state"),
                rs.getString("current_intent"),
                mapJson(rs, "slots"),
                mapJson(rs, "context_snapshot"),
                instant(rs, "last_message_at"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                instant(rs, "closed_at"));
    }

    private ChatMessageView mapMessage(ResultSet rs, int rowNum) throws SQLException {
        return new ChatMessageView(
                rs.getString("message_id"),
                rs.getString("trace_id"),
                rs.getString("session_id"),
                rs.getString("user_id"),
                rs.getString("role"),
                rs.getString("message_type"),
                rs.getString("content"),
                listJson(rs, "attachments"),
                listJson(rs, "quick_actions"),
                rs.getString("intent"),
                rs.getString("risk_level"),
                rs.getString("route_decision"),
                mapJson(rs, "metadata"),
                instant(rs, "created_at"));
    }

    private Map<String, Object> mapJson(ResultSet rs, String column) throws SQLException {
        String value = jsonColumn(rs, column);
        if (!hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return Map.of("raw", value);
        }
    }

    private List<Object> listJson(ResultSet rs, String column) throws SQLException {
        String value = jsonColumn(rs, column);
        if (!hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, LIST_TYPE);
        } catch (JsonProcessingException exception) {
            return List.of(value);
        }
    }

    private String jsonColumn(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : value.toString();
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
