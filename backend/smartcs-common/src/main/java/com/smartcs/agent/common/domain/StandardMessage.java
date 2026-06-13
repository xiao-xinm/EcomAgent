package com.smartcs.agent.common.domain;

import com.smartcs.agent.common.enums.MessageRole;
import com.smartcs.agent.common.enums.MessageType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Channel-neutral message contract accepted by gateway and agent core.
 */
public record StandardMessage(
        @NotBlank String messageId,
        @NotBlank String traceId,
        @NotBlank String sessionId,
        @NotBlank String userId,
        @NotBlank String channel,
        @NotNull MessageRole role,
        @NotNull MessageType messageType,
        String content,
        List<@Valid Attachment> attachments,
        Map<String, Object> metadata,
        @NotNull Instant createdAt
) {
}
