package com.smartcs.agent.common.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

/**
 * Minimal service health payload shared by backend services.
 */
public record ServiceHealth(
        @NotBlank String serviceName,
        @NotBlank String status,
        String version,
        String activeProfile,
        Map<String, Object> metadata,
        @NotNull Instant checkedAt
) {
}
