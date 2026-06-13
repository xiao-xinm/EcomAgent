package com.smartcs.agent.common.domain;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Minimal chat request shared by gateway and agent core.
 */
public record ChatRequest(
        String traceId,
        String sessionId,
        @NotBlank String userId,
        String channel,
        @NotBlank String content,
        Map<String, Object> metadata
) {
}
