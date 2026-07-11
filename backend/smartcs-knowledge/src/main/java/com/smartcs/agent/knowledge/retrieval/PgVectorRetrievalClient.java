package com.smartcs.agent.knowledge.retrieval;

import com.smartcs.agent.knowledge.embedding.EmbeddingClient;
import com.smartcs.agent.knowledge.embedding.EmbeddingClient.EmbeddingResult;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
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
    private static final String SOURCE = "pgvector-cosine-v1";
    private static final String SEARCH_SQL = """
            SELECT faq_id,
                   question,
                   answer,
                   category,
                   1 - (embedding <=> CAST(? AS vector)) AS similarity
            FROM knowledge_faq_embedding
            WHERE status = 'ACTIVE'
              AND embedding_model = ?
              AND embedding_dimensions = ?
            ORDER BY embedding <=> CAST(? AS vector)
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingClient embeddingClient;
    private final HikariDataSource dataSource;
    private final int dimensions;
    private final double minSimilarity;

    @Autowired
    public PgVectorRetrievalClient(
            EmbeddingClient embeddingClient,
            @Value("${smartcs.knowledge.vector.datasource.url}") String url,
            @Value("${smartcs.knowledge.vector.datasource.username}") String username,
            @Value("${smartcs.knowledge.vector.datasource.password}") String password,
            @Value("${smartcs.knowledge.vector.dimensions:1024}") int dimensions,
            @Value("${smartcs.knowledge.retrieval.vector-min-similarity:0.55}") double minSimilarity) {
        this(createDataSource(url, username, password), embeddingClient, dimensions, minSimilarity);
    }

    PgVectorRetrievalClient(
            JdbcTemplate jdbcTemplate,
            EmbeddingClient embeddingClient,
            int dimensions,
            double minSimilarity) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingClient = embeddingClient;
        this.dataSource = null;
        this.dimensions = validateDimensions(dimensions);
        this.minSimilarity = validateMinSimilarity(minSimilarity);
    }

    private PgVectorRetrievalClient(
            HikariDataSource dataSource,
            EmbeddingClient embeddingClient,
            int dimensions,
            double minSimilarity) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.embeddingClient = embeddingClient;
        this.dataSource = dataSource;
        this.dimensions = validateDimensions(dimensions);
        this.minSimilarity = validateMinSimilarity(minSimilarity);
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
            List<KnowledgeRetrievalCandidate> candidates = jdbcTemplate.query(
                    SEARCH_SQL,
                    (rs, rowNum) -> new KnowledgeRetrievalCandidate(
                            rs.getString("faq_id"),
                            rs.getString("question"),
                            rs.getString("answer"),
                            rs.getString("category"),
                            SOURCE,
                            rs.getDouble("similarity")),
                    vectorLiteral,
                    result.model(),
                    dimensions,
                    vectorLiteral,
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

    @PreDestroy
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private static HikariDataSource createDataSource(String url, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("smartcs-knowledge-vector");
        config.setDriverClassName("org.postgresql.Driver");
        config.setJdbcUrl(requireText(url, "vector datasource url"));
        config.setUsername(requireText(username, "vector datasource username"));
        config.setPassword(password == null ? "" : password);
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(3000L);
        config.setValidationTimeout(2000L);
        return new HikariDataSource(config);
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

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }
}
