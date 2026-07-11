package com.smartcs.agent.knowledge.document;

import java.time.Instant;
import java.util.List;

/** MySQL FAQ 投影到可重建检索索引时使用的统一文档。 */
public record FaqIndexDocument(
        String faqId,
        String question,
        String answer,
        List<String> keywords,
        String category,
        String status,
        int priority,
        String contentHash,
        Instant sourceUpdatedAt
) {
}
