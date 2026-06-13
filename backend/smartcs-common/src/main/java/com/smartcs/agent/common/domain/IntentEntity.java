package com.smartcs.agent.common.domain;

import com.smartcs.agent.common.enums.EntitySource;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Entity extracted from user utterance or context.
 */
public record IntentEntity(
        @NotBlank String name,
        Object value,
        @NotNull EntitySource source,
        @DecimalMin("0.0") @DecimalMax("1.0") Double confidence,
        Map<String, Object> metadata
) {
}
