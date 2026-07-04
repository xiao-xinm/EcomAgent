package com.smartcs.agent.knowledge.dto;

import java.time.Instant;
import java.util.List;

/**
 * FAQ management contracts for the Knowledge service.
 */
public final class FaqAdminDtos {

    private FaqAdminDtos() {
    }

    public record FaqItem(
            String faqId,
            String question,
            String answer,
            List<String> keywords,
            String category,
            String status,
            int priority,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record FaqUpsertRequest(
            String question,
            String answer,
            List<String> keywords,
            String category,
            String status,
            Integer priority
    ) {
    }

    public record FaqStatusRequest(
            String status
    ) {
    }
}
