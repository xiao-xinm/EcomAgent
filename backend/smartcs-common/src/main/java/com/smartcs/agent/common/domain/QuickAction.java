package com.smartcs.agent.common.domain;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * A user-selectable action rendered by frontend clients.
 */
public record QuickAction(
        @NotBlank String label,
        @NotBlank String value,
        String actionType,
        Map<String, Object> payload
) {
}
