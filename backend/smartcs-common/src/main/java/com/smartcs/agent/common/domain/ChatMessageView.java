package com.smartcs.agent.common.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 用户端拉取会话消息时返回的消息视图。
 */
public record ChatMessageView(
        String messageId,
        String traceId,
        String sessionId,
        String userId,
        String role,
        String messageType,
        String content,
        List<Object> attachments,
        List<Object> quickActions,
        String intent,
        String riskLevel,
        String routeDecision,
        Map<String, Object> metadata,
        Instant createdAt
) {
}
