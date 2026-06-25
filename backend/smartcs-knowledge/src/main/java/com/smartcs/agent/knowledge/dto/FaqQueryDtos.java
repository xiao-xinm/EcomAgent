package com.smartcs.agent.knowledge.dto;

import java.time.Instant;
import java.util.List;

/**
 * FAQ query contract for the minimal knowledge service.
 */
public final class FaqQueryDtos {

    private FaqQueryDtos() {
    }

    public record FaqQueryRequest(
            String traceId,
            String sessionId,
            String userId,
            String channel,
            String question
    ) {
    }

    public record FaqQueryResponse(
            String answerId,
            String question,
            String answer,
            boolean matched,
            double confidence,
            List<String> matchedKeywords,
            String source,
            Instant createdAt
    ) {
    }
}
