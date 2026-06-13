package com.smartcs.agent.common.domain;

import com.smartcs.agent.common.enums.EntitySource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

/**
 * Slot value used by dialog management and skill routing.
 */
public record SlotValue(
        @NotBlank String name,
        Object value,
        @NotNull EntitySource source,
        boolean required,
        boolean filled,
        boolean valid,
        String validationMessage,
        Map<String, Object> metadata,
        Instant updatedAt
) {
}
