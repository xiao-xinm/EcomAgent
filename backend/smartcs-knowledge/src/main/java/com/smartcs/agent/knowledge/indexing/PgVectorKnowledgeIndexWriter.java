package com.smartcs.agent.knowledge.indexing;

import com.smartcs.agent.knowledge.document.FaqIndexDocument;
import com.smartcs.agent.knowledge.embedding.EmbeddingClient;
import com.smartcs.agent.knowledge.embedding.EmbeddingClient.EmbeddingResult;
import com.smartcs.agent.knowledge.retrieval.PgVectorKnowledgeStore;
import com.smartcs.agent.knowledge.retrieval.VectorLiterals;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** pgvector FAQ 向量写入器。 */
@Service
@ConditionalOnProperty(name = "smartcs.knowledge.retrieval.mode", havingValue = "hybrid")
public class PgVectorKnowledgeIndexWriter implements KnowledgeIndexWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(PgVectorKnowledgeIndexWriter.class);
    private static final String WRITER = "pgvector";

    private final PgVectorKnowledgeStore store;
    private final EmbeddingClient embeddingClient;
    private final int dimensions;

    public PgVectorKnowledgeIndexWriter(
            PgVectorKnowledgeStore store,
            EmbeddingClient embeddingClient,
            @Value("${smartcs.knowledge.vector.dimensions:1024}") int dimensions) {
        this.store = store;
        this.embeddingClient = embeddingClient;
        this.dimensions = dimensions;
    }

    @Override
    public String name() {
        return WRITER;
    }

    @Override
    public IndexOperationResult upsert(FaqIndexDocument document) {
        try {
            if (!"ACTIVE".equals(document.status())) {
                store.delete(document.faqId());
                return IndexOperationResult.success(WRITER, "deleted-inactive:" + document.faqId());
            }
            Optional<EmbeddingResult> embedding = embeddingClient.embed(embeddingText(document));
            if (embedding.isEmpty()) {
                return IndexOperationResult.failure(WRITER, "embedding unavailable");
            }
            EmbeddingResult result = embedding.orElseThrow();
            if (result.vector().size() != dimensions) {
                return IndexOperationResult.failure(
                        WRITER,
                        "embedding dimensions mismatch expected=" + dimensions + " actual=" + result.vector().size());
            }
            store.upsert(document, result.model(), dimensions, VectorLiterals.from(result.vector()));
            return IndexOperationResult.success(WRITER, "upserted:" + document.faqId());
        } catch (DataAccessException | IllegalArgumentException exception) {
            LOGGER.warn("pgvector FAQ 索引写入失败 faqId={} reason={}", document.faqId(), exception.getMessage());
            return IndexOperationResult.failure(WRITER, exception.getMessage());
        }
    }

    @Override
    public IndexOperationResult reset() {
        try {
            int deleted = store.deleteAll();
            return IndexOperationResult.success(WRITER, "reset:" + deleted);
        } catch (DataAccessException exception) {
            LOGGER.warn("pgvector FAQ 索引清空失败 reason={}", exception.getMessage());
            return IndexOperationResult.failure(WRITER, exception.getMessage());
        }
    }

    private String embeddingText(FaqIndexDocument document) {
        return document.question()
                + "\n"
                + document.answer()
                + "\n"
                + String.join(" ", document.keywords());
    }
}
