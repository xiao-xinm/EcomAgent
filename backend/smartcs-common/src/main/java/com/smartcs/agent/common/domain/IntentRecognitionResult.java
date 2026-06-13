package com.smartcs.agent.common.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * Structured result produced by the NLU stage.
 */
public record IntentRecognitionResult(
        @NotBlank String traceId,
        @NotBlank String sessionId,
        @NotBlank String intent,
        @DecimalMin("0.0") @DecimalMax("1.0") double confidence,
        List<@Valid IntentEntity> entities,
        String reasoning,
        Map<String, Object> metadata
) {
}
