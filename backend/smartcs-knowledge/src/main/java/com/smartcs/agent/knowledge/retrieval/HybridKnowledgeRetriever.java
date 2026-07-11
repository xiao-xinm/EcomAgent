package com.smartcs.agent.knowledge.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Elasticsearch 与 pgvector 双路召回编排器。
 *
 * <p>使用排名而非原始分数执行 RRF，避免直接比较 BM25 与余弦相似度。任一路为空时仍可返回
 * 单路结果；两路都为空时交给 FaqKnowledgeService 使用 MySQL/内置 FAQ 兜底。
 */
@Service
public class HybridKnowledgeRetriever {

    private static final Logger LOGGER = LoggerFactory.getLogger(HybridKnowledgeRetriever.class);
    private static final String HYBRID_SOURCE = "hybrid-rrf-v1";

    private final KeywordRetrievalClient keywordRetrievalClient;
    private final VectorRetrievalClient vectorRetrievalClient;
    private final boolean hybridEnabled;
    private final int elasticsearchTopK;
    private final int vectorTopK;
    private final int finalTopK;
    private final int rrfK;
    private final double keywordOnlyConfidence;

    @Autowired
    public HybridKnowledgeRetriever(
            KeywordRetrievalClient keywordRetrievalClient,
            ObjectProvider<VectorRetrievalClient> vectorRetrievalClientProvider,
            @Value("${smartcs.knowledge.retrieval.mode:keyword}") String mode,
            @Value("${smartcs.knowledge.retrieval.elasticsearch-top-k:10}") int elasticsearchTopK,
            @Value("${smartcs.knowledge.retrieval.vector-top-k:10}") int vectorTopK,
            @Value("${smartcs.knowledge.retrieval.final-top-k:5}") int finalTopK,
            @Value("${smartcs.knowledge.retrieval.rrf-k:60}") int rrfK,
            @Value("${smartcs.knowledge.retrieval.keyword-only-confidence:0.68}")
                    double keywordOnlyConfidence) {
        this(
                keywordRetrievalClient,
                vectorRetrievalClientProvider.getIfAvailable(),
                mode,
                elasticsearchTopK,
                vectorTopK,
                finalTopK,
                rrfK,
                keywordOnlyConfidence);
    }

    HybridKnowledgeRetriever(
            KeywordRetrievalClient keywordRetrievalClient,
            VectorRetrievalClient vectorRetrievalClient,
            String mode,
            int elasticsearchTopK,
            int vectorTopK,
            int finalTopK,
            int rrfK,
            double keywordOnlyConfidence) {
        this.keywordRetrievalClient = keywordRetrievalClient;
        this.vectorRetrievalClient = vectorRetrievalClient;
        this.hybridEnabled = "hybrid".equals(normalizeMode(mode));
        this.elasticsearchTopK = requirePositive(elasticsearchTopK, "elasticsearchTopK");
        this.vectorTopK = requirePositive(vectorTopK, "vectorTopK");
        this.finalTopK = requirePositive(finalTopK, "finalTopK");
        this.rrfK = requirePositive(rrfK, "rrfK");
        this.keywordOnlyConfidence = requireConfidence(keywordOnlyConfidence);
    }

    public Optional<HybridRetrievalResult> retrieve(String question) {
        if (!hybridEnabled || question == null || question.isBlank()) {
            return Optional.empty();
        }

        List<KnowledgeRetrievalCandidate> keywordCandidates =
                keywordRetrievalClient.search(question, elasticsearchTopK);
        List<KnowledgeRetrievalCandidate> vectorCandidates = vectorRetrievalClient == null
                ? List.of()
                : vectorRetrievalClient.search(question, vectorTopK);
        List<FusedCandidate> fused = fuse(keywordCandidates, vectorCandidates);
        if (fused.isEmpty()) {
            LOGGER.info("混合知识检索无候选，回退 MySQL FAQ questionLength={}", question.length());
            return Optional.empty();
        }

        FusedCandidate winner = fused.get(0);
        LOGGER.info(
                "混合知识检索完成 faqId={} sources={} rrfScore={} keywordHits={} vectorHits={}",
                winner.candidate().faqId(),
                winner.sources(),
                winner.rrfScore(),
                keywordCandidates.size(),
                vectorCandidates.size());
        return Optional.of(new HybridRetrievalResult(
                winner.candidate(),
                confidence(winner),
                winner.sources().size() > 1 ? HYBRID_SOURCE : winner.candidate().source(),
                winner.rrfScore(),
                List.copyOf(winner.sources())));
    }

    private List<FusedCandidate> fuse(
            List<KnowledgeRetrievalCandidate> keywordCandidates,
            List<KnowledgeRetrievalCandidate> vectorCandidates) {
        Map<String, MutableFusedCandidate> merged = new LinkedHashMap<>();
        addRankedCandidates(merged, keywordCandidates);
        addRankedCandidates(merged, vectorCandidates);
        return merged.values().stream()
                .map(MutableFusedCandidate::freeze)
                .sorted(Comparator.comparingDouble(FusedCandidate::rrfScore)
                        .reversed()
                        .thenComparing(candidate -> candidate.candidate().faqId()))
                .limit(finalTopK)
                .toList();
    }

    private void addRankedCandidates(
            Map<String, MutableFusedCandidate> merged,
            List<KnowledgeRetrievalCandidate> candidates) {
        for (int index = 0; index < candidates.size(); index++) {
            KnowledgeRetrievalCandidate candidate = candidates.get(index);
            if (candidate == null || candidate.faqId() == null || candidate.faqId().isBlank()) {
                continue;
            }
            int rank = index + 1;
            merged.computeIfAbsent(candidate.faqId(), ignored -> new MutableFusedCandidate(candidate))
                    .add(candidate, 1.0D / (rrfK + rank));
        }
    }

    private double confidence(FusedCandidate candidate) {
        if (candidate.sources().size() > 1) {
            return 0.90D;
        }
        if (candidate.sources().contains("pgvector-cosine-v1")) {
            return Math.max(0.0D, Math.min(0.95D, candidate.maxVectorSimilarity()));
        }
        return keywordOnlyConfidence;
    }

    private String normalizeMode(String value) {
        return value == null ? "keyword" : value.trim().toLowerCase(Locale.ROOT);
    }

    private int requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " 必须大于 0");
        }
        return value;
    }

    private double requireConfidence(double value) {
        if (!Double.isFinite(value) || value < 0.0D || value > 1.0D) {
            throw new IllegalArgumentException("keywordOnlyConfidence 必须在 0 到 1 之间");
        }
        return value;
    }

    public record HybridRetrievalResult(
            KnowledgeRetrievalCandidate candidate,
            double confidence,
            String source,
            double rrfScore,
            List<String> sources
    ) {
    }

    private record FusedCandidate(
            KnowledgeRetrievalCandidate candidate,
            double rrfScore,
            Set<String> sources,
            double maxVectorSimilarity
    ) {
    }

    private static final class MutableFusedCandidate {

        private KnowledgeRetrievalCandidate candidate;
        private double rrfScore;
        private final Set<String> sources = new LinkedHashSet<>();
        private double maxVectorSimilarity;

        private MutableFusedCandidate(KnowledgeRetrievalCandidate candidate) {
            this.candidate = candidate;
        }

        private MutableFusedCandidate add(KnowledgeRetrievalCandidate value, double rankScore) {
            rrfScore += rankScore;
            sources.add(value.source());
            if ("pgvector-cosine-v1".equals(value.source())) {
                maxVectorSimilarity = Math.max(maxVectorSimilarity, value.score());
            }
            if (candidate.answer() == null || candidate.answer().isBlank()) {
                candidate = value;
            }
            return this;
        }

        private FusedCandidate freeze() {
            return new FusedCandidate(candidate, rrfScore, Set.copyOf(sources), maxVectorSimilarity);
        }
    }
}
