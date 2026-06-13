package com.smartcs.agent.common.domain;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Attachment metadata shared across channels.
 */
public record Attachment(
        @NotBlank String attachmentId,
        @NotBlank String url,
        String name,
        String contentType,
        Long size,
        Map<String, Object> metadata
) {
}
