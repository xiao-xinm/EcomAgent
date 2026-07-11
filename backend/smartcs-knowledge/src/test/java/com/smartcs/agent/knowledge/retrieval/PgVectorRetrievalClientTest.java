package com.smartcs.agent.knowledge.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcs.agent.knowledge.embedding.EmbeddingClient;
import com.smartcs.agent.knowledge.embedding.EmbeddingClient.EmbeddingResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class PgVectorRetrievalClientTest {

    @Test
    void searchUsesEmbeddingAndFiltersLowSimilarityCandidates() {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate(List.of(
                candidate("faq_refund_arrival", 0.82D),
                candidate("faq_return_policy", 0.41D)));
        EmbeddingClient embeddingClient = text -> Optional.of(new EmbeddingResult(
                "text-embedding-v4",
                List.of(0.1D, -0.2D, 0.3D),
                6));
        PgVectorRetrievalClient client = new PgVectorRetrievalClient(jdbcTemplate, embeddingClient, 3, 0.55D);

        List<KnowledgeRetrievalCandidate> results = client.search("退款多久能到账", 5);

        assertThat(results).extracting(KnowledgeRetrievalCandidate::faqId)
                .containsExactly("faq_refund_arrival");
        assertThat(jdbcTemplate.arguments).containsExactly(
                "[0.1,-0.2,0.3]",
                "text-embedding-v4",
                3,
                "[0.1,-0.2,0.3]",
                5);
    }

    @Test
    void searchDoesNotQueryPostgresWhenEmbeddingFails() {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate(List.of());
        EmbeddingClient embeddingClient = text -> Optional.empty();
        PgVectorRetrievalClient client = new PgVectorRetrievalClient(jdbcTemplate, embeddingClient, 3, 0.55D);

        assertThat(client.search("退款多久能到账", 5)).isEmpty();
        assertThat(jdbcTemplate.arguments).isNull();
    }

    @Test
    void searchReturnsEmptyWhenPostgresIsUnavailable() {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate(List.of());
        jdbcTemplate.failure = new DataAccessResourceFailureException("postgres unavailable");
        EmbeddingClient embeddingClient = text -> Optional.of(new EmbeddingResult(
                "text-embedding-v4",
                List.of(0.1D, -0.2D, 0.3D),
                6));
        PgVectorRetrievalClient client = new PgVectorRetrievalClient(jdbcTemplate, embeddingClient, 3, 0.55D);

        assertThat(client.search("退款多久能到账", 5)).isEmpty();
    }

    @Test
    void searchReturnsEmptyWhenEmbeddingDimensionsDoNotMatch() {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate(List.of());
        EmbeddingClient embeddingClient = text -> Optional.of(new EmbeddingResult(
                "text-embedding-v4",
                List.of(0.1D, -0.2D),
                6));
        PgVectorRetrievalClient client = new PgVectorRetrievalClient(jdbcTemplate, embeddingClient, 3, 0.55D);

        assertThat(client.search("退款多久能到账", 5)).isEmpty();
        assertThat(jdbcTemplate.arguments).isNull();
    }

    private KnowledgeRetrievalCandidate candidate(String faqId, double similarity) {
        return new KnowledgeRetrievalCandidate(
                faqId,
                "标准问题",
                "标准答案",
                "after_sale",
                "pgvector-cosine-v1",
                similarity);
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {

        private final List<KnowledgeRetrievalCandidate> results;
        private Object[] arguments;
        private DataAccessResourceFailureException failure;

        private StubJdbcTemplate(List<KnowledgeRetrievalCandidate> results) {
            this.results = results;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            arguments = args;
            if (failure != null) {
                throw failure;
            }
            return (List<T>) results;
        }
    }
}
