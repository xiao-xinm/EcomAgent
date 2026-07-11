package com.smartcs.agent.knowledge.retrieval;

import com.smartcs.agent.knowledge.document.FaqIndexDocument;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Knowledge 专用 pgvector 存储网关。
 *
 * <p>内部持有独立连接池，但不向 Spring 注册第二个 DataSource/JdbcTemplate，保证 MySQL 自动配置不受影响。
 */
@Repository
@ConditionalOnProperty(name = "smartcs.knowledge.retrieval.mode", havingValue = "hybrid")
public class PgVectorKnowledgeStore {

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
    private static final String SOURCE = "pgvector-cosine-v1";
    private static final String UPSERT_SQL = """
            INSERT INTO knowledge_faq_embedding
                (faq_id, question, answer, category, status, content_hash, source_updated_at,
                 embedding_model, embedding_dimensions, embedding, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS vector), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (faq_id) DO UPDATE SET
                question = EXCLUDED.question,
                answer = EXCLUDED.answer,
                category = EXCLUDED.category,
                status = EXCLUDED.status,
                content_hash = EXCLUDED.content_hash,
                source_updated_at = EXCLUDED.source_updated_at,
                embedding_model = EXCLUDED.embedding_model,
                embedding_dimensions = EXCLUDED.embedding_dimensions,
                embedding = EXCLUDED.embedding,
                updated_at = CURRENT_TIMESTAMP
            """;

    private final JdbcTemplate jdbcTemplate;
    private final HikariDataSource dataSource;

    @Autowired
    public PgVectorKnowledgeStore(
            @Value("${smartcs.knowledge.vector.datasource.url}") String url,
            @Value("${smartcs.knowledge.vector.datasource.username}") String username,
            @Value("${smartcs.knowledge.vector.datasource.password}") String password) {
        this(createDataSource(url, username, password));
    }

    PgVectorKnowledgeStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = null;
    }

    private PgVectorKnowledgeStore(HikariDataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.dataSource = dataSource;
    }

    public List<KnowledgeRetrievalCandidate> search(
            String vectorLiteral,
            String model,
            int dimensions,
            int topK) {
        return jdbcTemplate.query(
                SEARCH_SQL,
                (rs, rowNum) -> new KnowledgeRetrievalCandidate(
                        rs.getString("faq_id"),
                        rs.getString("question"),
                        rs.getString("answer"),
                        rs.getString("category"),
                        SOURCE,
                        rs.getDouble("similarity")),
                vectorLiteral,
                model,
                dimensions,
                vectorLiteral,
                topK);
    }

    public int upsert(FaqIndexDocument document, String model, int dimensions, String vectorLiteral) {
        return jdbcTemplate.update(
                UPSERT_SQL,
                document.faqId(),
                document.question(),
                document.answer(),
                document.category(),
                document.status(),
                document.contentHash(),
                java.sql.Timestamp.from(document.sourceUpdatedAt()),
                model,
                dimensions,
                vectorLiteral);
    }

    public int delete(String faqId) {
        return jdbcTemplate.update("DELETE FROM knowledge_faq_embedding WHERE faq_id = ?", faqId);
    }

    public int deleteAll() {
        return jdbcTemplate.update("DELETE FROM knowledge_faq_embedding");
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

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }
}
