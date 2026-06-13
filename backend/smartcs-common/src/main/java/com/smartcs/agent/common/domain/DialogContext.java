package com.smartcs.agent.common.domain;

import com.smartcs.agent.common.enums.DialogState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Conversation context snapshot passed between services.
 */
public record DialogContext(
        @NotBlank String traceId,
        @NotBlank String sessionId,
        @NotBlank String userId,
        @NotNull DialogState state,
        String currentIntent,
        Map<String, @Valid SlotValue> slots,
        List<@Valid StandardMessage> recentMessages,
        Map<String, Object> attributes,
        @NotNull Instant updatedAt
) {
}
