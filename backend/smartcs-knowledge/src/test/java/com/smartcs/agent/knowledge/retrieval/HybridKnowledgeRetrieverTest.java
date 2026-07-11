package com.smartcs.agent.knowledge.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class HybridKnowledgeRetrieverTest {

    @Test
    void retrievePrefersCandidateConfirmedByBothRetrievers() {
        KeywordRetrievalClient keyword = (question, topK) -> List.of(
                candidate("faq_keyword_only", "elasticsearch-bm25-v1", 10.0D),
                candidate("faq_shared", "elasticsearch-bm25-v1", 8.0D));
        VectorRetrievalClient vector = (question, topK) -> List.of(
                candidate("faq_shared", "pgvector-cosine-v1", 0.84D),
                candidate("faq_vector_only", "pgvector-cosine-v1", 0.80D));
        HybridKnowledgeRetriever retriever = retriever(keyword, vector, "hybrid");

        HybridKnowledgeRetriever.HybridRetrievalResult result =
                retriever.retrieve("退款多久能到账").orElseThrow();

        assertThat(result.candidate().faqId()).isEqualTo("faq_shared");
        assertThat(result.source()).isEqualTo("hybrid-rrf-v1");
        assertThat(result.confidence()).isEqualTo(0.90D);
        assertThat(result.sources()).containsExactlyInAnyOrder("elasticsearch-bm25-v1", "pgvector-cosine-v1");
    }

    @Test
    void retrieveUsesVectorConfidenceWhenKeywordRetrievalIsEmpty() {
        KeywordRetrievalClient keyword = (question, topK) -> List.of();
        VectorRetrievalClient vector = (question, topK) -> List.of(
                candidate("faq_vector", "pgvector-cosine-v1", 0.82D));
        HybridKnowledgeRetriever retriever = retriever(keyword, vector, "hybrid");

        HybridKnowledgeRetriever.HybridRetrievalResult result =
                retriever.retrieve("退钱一般要几天").orElseThrow();

        assertThat(result.source()).isEqualTo("pgvector-cosine-v1");
        assertThat(result.confidence()).isEqualTo(0.82D);
    }

    @Test
    void retrieveUsesConfiguredConfidenceForKeywordOnlyResult() {
        KeywordRetrievalClient keyword = (question, topK) -> List.of(
                candidate("faq_keyword", "elasticsearch-bm25-v1", 6.0D));
        HybridKnowledgeRetriever retriever = retriever(keyword, null, "hybrid");

        HybridKnowledgeRetriever.HybridRetrievalResult result =
                retriever.retrieve("退款规则").orElseThrow();

        assertThat(result.source()).isEqualTo("elasticsearch-bm25-v1");
        assertThat(result.confidence()).isEqualTo(0.68D);
    }

    @Test
    void retrieveDoesNotCallRemoteClientsInKeywordMode() {
        AtomicBoolean called = new AtomicBoolean(false);
        KeywordRetrievalClient keyword = (question, topK) -> {
            called.set(true);
            return List.of();
        };
        HybridKnowledgeRetriever retriever = retriever(keyword, null, "keyword");

        assertThat(retriever.retrieve("退款规则")).isEmpty();
        assertThat(called).isFalse();
    }

    private HybridKnowledgeRetriever retriever(
            KeywordRetrievalClient keyword,
            VectorRetrievalClient vector,
            String mode) {
        return new HybridKnowledgeRetriever(keyword, vector, mode, 10, 10, 5, 60, 0.68D);
    }

    private KnowledgeRetrievalCandidate candidate(String faqId, String source, double score) {
        return new KnowledgeRetrievalCandidate(
                faqId,
                "标准问题",
                "标准答案",
                "after_sale",
                source,
                score);
    }
}
