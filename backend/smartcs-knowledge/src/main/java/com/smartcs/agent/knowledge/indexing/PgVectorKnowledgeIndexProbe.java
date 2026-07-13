package com.smartcs.agent.knowledge.indexing;

import com.smartcs.agent.knowledge.retrieval.PgVectorKnowledgeStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** pgvector 当前模型和维度的 ACTIVE FAQ 向量计数探针。 */
@Service
@ConditionalOnProperty(name = "smartcs.knowledge.retrieval.mode", havingValue = "hybrid")
public class PgVectorKnowledgeIndexProbe implements KnowledgeIndexProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger(PgVectorKnowledgeIndexProbe.class);
    private static final String NAME = "pgvector";

    private final PgVectorKnowledgeStore store;
    private final String model;
    private final int dimensions;

    public PgVectorKnowledgeIndexProbe(
            PgVectorKnowledgeStore store,
            @Value("${smartcs.knowledge.embedding.model:text-embedding-v4}") String model,
            @Value("${smartcs.knowledge.vector.dimensions:1024}") int dimensions) {
        this.store = store;
        this.model = requireText(model, "embedding model");
        if (dimensions <= 0) {
            throw new IllegalArgumentException("embedding dimensions 必须大于 0");
        }
        this.dimensions = dimensions;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ProbeResult probe() {
        try {
            return ProbeResult.ready(NAME, store.countActive(model, dimensions));
        } catch (DataAccessException | IllegalArgumentException exception) {
            LOGGER.warn("pgvector FAQ 索引状态检查失败 reason={}", exception.getMessage());
            return ProbeResult.unavailable(NAME, safeMessage(exception));
        }
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
