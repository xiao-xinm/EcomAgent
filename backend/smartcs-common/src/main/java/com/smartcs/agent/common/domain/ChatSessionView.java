package com.smartcs.agent.common.domain;

import java.time.Instant;
import java.util.Map;

/**
 * 用户端查询当前客服会话状态时返回的会话视图。
 */
public record ChatSessionView(
        String sessionId,
        String traceId,
        String userId,
        String channel,
        String status,
        String dialogState,
        String currentIntent,
        Map<String, Object> slots,
        Map<String, Object> contextSnapshot,
        Instant lastMessageAt,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt
) {
}
