package com.smartcs.agent.common.domain;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * 用户点击快捷操作后的请求，例如确认继续或取消操作。
 */
public record ChatActionRequest(
        String traceId,
        @NotBlank String sessionId,
        @NotBlank String userId,
        String channel,
        String actionId,
        @NotBlank String actionType,
        String content,
        Map<String, Object> payload,
        Map<String, Object> metadata
) {
}
