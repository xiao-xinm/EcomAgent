package com.smartcs.agent.knowledge.dto;

import java.time.Instant;
import java.util.List;

/** 混合知识索引状态接口契约。 */
public final class KnowledgeIndexStatusDtos {

    private KnowledgeIndexStatusDtos() {
    }

    public record KnowledgeStoreStatus(
            String name,
            boolean available,
            Long documentCount,
            String message
    ) {
    }

    public record KnowledgeIndexStatus(
            String mode,
            boolean enabled,
            boolean healthy,
            boolean consistent,
            KnowledgeStoreStatus source,
            List<KnowledgeStoreStatus> indexes,
            Instant checkedAt
    ) {
    }
}
