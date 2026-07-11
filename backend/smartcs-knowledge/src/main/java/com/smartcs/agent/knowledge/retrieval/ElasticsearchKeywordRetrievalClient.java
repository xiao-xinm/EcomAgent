package com.smartcs.agent.knowledge.retrieval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Elasticsearch FAQ 关键词检索客户端。
 *
 * <p>查询只召回 ACTIVE 文档，并对标准问题、关键词和答案设置不同权重。调用失败时返回空列表，
 * 由上层混合检索降级到 pgvector 或现有 MySQL FAQ。
 */
@Service
public class ElasticsearchKeywordRetrievalClient implements KeywordRetrievalClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchKeywordRetrievalClient.class);
    private static final String SOURCE = "elasticsearch-bm25-v1";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String index;

    public ElasticsearchKeywordRetrievalClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${smartcs.knowledge.elasticsearch.url:http://localhost:9200}") String baseUrl,
            @Value("${smartcs.knowledge.elasticsearch.username:}") String username,
            @Value("${smartcs.knowledge.elasticsearch.password:}") String password,
            @Value("${smartcs.knowledge.elasticsearch.index:smartcs_knowledge_faq_v1}") String index) {
        RestClient.Builder configuredBuilder = restClientBuilder.baseUrl(trimTrailingSlash(baseUrl));
        if (username != null && !username.isBlank()) {
            String encoded = HttpHeaders.encodeBasicAuth(
                    username.trim(), password == null ? "" : password, StandardCharsets.UTF_8);
            configuredBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        }
        this.restClient = configuredBuilder.build();
        this.objectMapper = objectMapper;
        this.index = requireText(index, "elasticsearch index");
    }

    @Override
    public List<KnowledgeRetrievalCandidate> search(String question, int topK) {
        if (question == null || question.isBlank() || topK <= 0) {
            return List.of();
        }

        try {
            LOGGER.info("Elasticsearch FAQ 检索开始 index={} topK={} questionLength={}", index, topK, question.length());
            String body = restClient.post()
                    .uri("/{index}/_search", index)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(searchBody(question, topK))
                    .retrieve()
                    .body(String.class);
            List<KnowledgeRetrievalCandidate> candidates = parseResponse(body);
            LOGGER.info("Elasticsearch FAQ 检索完成 index={} hitCount={}", index, candidates.size());
            return candidates;
        } catch (RestClientException | JsonProcessingException | IllegalArgumentException exception) {
            LOGGER.warn(
                    "Elasticsearch FAQ 检索失败，关键词召回将降级 index={} reason={}",
                    index,
                    exception.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> searchBody(String question, int topK) {
        Map<String, Object> multiMatch = Map.of(
                "query", question,
                "fields", List.of("question^4", "keywords^3", "answer"),
                "type", "best_fields",
                "operator", "or");
        Map<String, Object> boolQuery = Map.of(
                "filter", List.of(Map.of("term", Map.of("status", "ACTIVE"))),
                "must", List.of(Map.of("multi_match", multiMatch)));
        return Map.of(
                "size", topK,
                "track_total_hits", false,
                "_source", List.of("faqId", "question", "answer", "category"),
                "query", Map.of("bool", boolQuery));
    }

    private List<KnowledgeRetrievalCandidate> parseResponse(String body) throws JsonProcessingException {
        JsonNode hits = objectMapper.readTree(requireText(body, "elasticsearch response")).path("hits").path("hits");
        if (!hits.isArray()) {
            throw new IllegalArgumentException("Elasticsearch 响应缺少 hits.hits");
        }

        List<KnowledgeRetrievalCandidate> candidates = new ArrayList<>(hits.size());
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            String faqId = source.path("faqId").asText("");
            String question = source.path("question").asText("");
            String answer = source.path("answer").asText("");
            if (faqId.isBlank() || question.isBlank() || answer.isBlank()) {
                LOGGER.warn("Elasticsearch FAQ 命中文档字段不完整，跳过 docId={}", hit.path("_id").asText(""));
                continue;
            }
            candidates.add(new KnowledgeRetrievalCandidate(
                    faqId,
                    question,
                    answer,
                    source.path("category").asText("general"),
                    SOURCE,
                    hit.path("_score").asDouble(0.0D)));
        }
        return List.copyOf(candidates);
    }

    private String trimTrailingSlash(String value) {
        String normalized = requireText(value, "elasticsearch url");
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }
}
