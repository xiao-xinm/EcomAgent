package com.smartcs.agent.core.knowledge;

import java.time.Instant;
import java.util.List;

/**
 * Local DTOs used by Agent Core to call the Knowledge FAQ API.
 */
public final class KnowledgeFaqDtos {

    private KnowledgeFaqDtos() {
    }

    public record FaqQueryRequest(
            String traceId,
            String sessionId,
            String userId,
            String channel,
            String question
    ) {
    }

    public record FaqQueryResult(
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
