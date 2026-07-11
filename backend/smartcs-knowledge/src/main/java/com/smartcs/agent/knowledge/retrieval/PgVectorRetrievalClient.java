package com.smartcs.agent.knowledge.retrieval;

import com.smartcs.agent.knowledge.embedding.EmbeddingClient;
import com.smartcs.agent.knowledge.embedding.EmbeddingClient.EmbeddingResult;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * pgvector FAQ 语义检索客户端。
 *
 * <p>只在 retrieval.mode=hybrid 时创建独立 PostgreSQL 连接池，不向 Spring 容器暴露第二个
 * DataSource/JdbcTemplate，避免影响现有 MySQL FAQ 管理链路。
 */
@Service
@ConditionalOnProperty(name = "smartcs.knowledge.retrieval.mode", havingValue = "hybrid")
public class PgVectorRetrievalClient implements VectorRetrievalClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PgVectorRetrievalClient.class);
    private final PgVectorKnowledgeStore store;
    private final EmbeddingClient embeddingClient;
    private final int dimensions;
    private final double minSimilarity;

    @Autowired
    public PgVectorRetrievalClient(
            PgVectorKnowledgeStore store,
            EmbeddingClient embeddingClient,
            @Value("${smartcs.knowledge.vector.dimensions:1024}") int dimensions,
            @Value("${smartcs.knowledge.retrieval.vector-min-similarity:0.55}") double minSimilarity) {
        this.store = store;
        this.embeddingClient = embeddingClient;
        this.dimensions = validateDimensions(dimensions);
        this.minSimilarity = validateMinSimilarity(minSimilarity);
    }

    PgVectorRetrievalClient(
            JdbcTemplate jdbcTemplate,
            EmbeddingClient embeddingClient,
            int dimensions,
            double minSimilarity) {
        this(new PgVectorKnowledgeStore(jdbcTemplate), embeddingClient, dimensions, minSimilarity);
    }

    @Override
    public List<KnowledgeRetrievalCandidate> search(String question, int topK) {
        if (question == null || question.isBlank() || topK <= 0) {
            return List.of();
        }

        Optional<EmbeddingResult> embedding = embeddingClient.embed(question);
        if (embedding.isEmpty()) {
            return List.of();
        }
        EmbeddingResult result = embedding.orElseThrow();
        if (result.vector().size() != dimensions) {
            LOGGER.warn(
                    "pgvector 查询向量维度不匹配 model={} expected={} actual={}",
                    result.model(),
                    dimensions,
                    result.vector().size());
            return List.of();
        }

        String vectorLiteral = toVectorLiteral(result.vector());
        try {
            LOGGER.info(
                    "pgvector FAQ 检索开始 model={} dimensions={} topK={} questionLength={}",
                    result.model(),
                    dimensions,
                    topK,
                    question.length());
            List<KnowledgeRetrievalCandidate> candidates = store.search(
                    vectorLiteral,
                    result.model(),
                    dimensions,
                    topK);
            List<KnowledgeRetrievalCandidate> filtered = candidates.stream()
                    .filter(candidate -> Double.isFinite(candidate.score()))
                    .filter(candidate -> candidate.score() >= minSimilarity)
                    .toList();
            LOGGER.info(
                    "pgvector FAQ 检索完成 candidateCount={} acceptedCount={} minSimilarity={}",
                    candidates.size(),
                    filtered.size(),
                    minSimilarity);
            return filtered;
        } catch (DataAccessException | IllegalArgumentException exception) {
            LOGGER.warn("pgvector FAQ 检索失败，语义召回将降级 reason={}", exception.getMessage());
            return List.of();
        }
    }

    private String toVectorLiteral(List<Double> vector) {
        return vector.stream()
                .peek(value -> {
                    if (value == null || !Double.isFinite(value)) {
                        throw new IllegalArgumentException("Embedding 包含非法数值");
                    }
                })
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static int validateDimensions(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("vector dimensions 必须大于 0");
        }
        return value;
    }

    private static double validateMinSimilarity(double value) {
        if (!Double.isFinite(value) || value < -1.0D || value > 1.0D) {
            throw new IllegalArgumentException("vector minSimilarity 必须在 -1 到 1 之间");
        }
        return value;
    }

}
