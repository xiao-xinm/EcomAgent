package com.smartcs.agent.knowledge.indexing;

import com.smartcs.agent.knowledge.document.FaqIndexDocument;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Elasticsearch FAQ 检索文档写入器。 */
@Service
@ConditionalOnProperty(name = "smartcs.knowledge.retrieval.mode", havingValue = "hybrid")
public class ElasticsearchKnowledgeIndexWriter implements KnowledgeIndexWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchKnowledgeIndexWriter.class);
    private static final String WRITER = "elasticsearch";

    private final RestClient restClient;
    private final String index;
    private final AtomicBoolean indexReady = new AtomicBoolean(false);

    public ElasticsearchKnowledgeIndexWriter(
            RestClient.Builder restClientBuilder,
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
        this.index = requireText(index, "elasticsearch index");
    }

    @Override
    public String name() {
        return WRITER;
    }

    @Override
    public IndexOperationResult upsert(FaqIndexDocument document) {
        try {
            ensureIndex();
            restClient.put()
                    .uri("/{index}/_doc/{faqId}?refresh=wait_for", index, document.faqId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "faqId", document.faqId(),
                            "question", document.question(),
                            "answer", document.answer(),
                            "keywords", document.keywords(),
                            "category", document.category(),
                            "status", document.status(),
                            "priority", document.priority(),
                            "contentHash", document.contentHash(),
                            "sourceUpdatedAt", document.sourceUpdatedAt().toString()))
                    .retrieve()
                    .toBodilessEntity();
            return IndexOperationResult.success(WRITER, "upserted:" + document.faqId());
        } catch (RestClientException | IllegalArgumentException exception) {
            LOGGER.warn("Elasticsearch FAQ 索引写入失败 faqId={} reason={}", document.faqId(), exception.getMessage());
            indexReady.set(false);
            return IndexOperationResult.failure(WRITER, exception.getMessage());
        }
    }

    @Override
    public IndexOperationResult reset() {
        try {
            ensureIndex();
            restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{index}/_delete_by_query")
                            .queryParam("refresh", true)
                            .queryParam("conflicts", "proceed")
                            .build(index))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("query", Map.of("match_all", Map.of())))
                    .retrieve()
                    .toBodilessEntity();
            return IndexOperationResult.success(WRITER, "reset");
        } catch (RestClientException | IllegalArgumentException exception) {
            LOGGER.warn("Elasticsearch FAQ 索引清空失败 index={} reason={}", index, exception.getMessage());
            indexReady.set(false);
            return IndexOperationResult.failure(WRITER, exception.getMessage());
        }
    }

    private void ensureIndex() {
        if (indexReady.get()) {
            return;
        }
        synchronized (indexReady) {
            if (indexReady.get()) {
                return;
            }
            try {
                restClient.head().uri("/{index}", index).retrieve().toBodilessEntity();
            } catch (HttpClientErrorException.NotFound exception) {
                restClient.put()
                        .uri("/{index}", index)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(indexDefinition())
                        .retrieve()
                        .toBodilessEntity();
            }
            indexReady.set(true);
        }
    }

    private Map<String, Object> indexDefinition() {
        Map<String, Object> analyzer = Map.of(
                "smartcs_zh", Map.of(
                        "type", "custom",
                        "tokenizer", "smartcn_tokenizer",
                        "filter", List.of("lowercase", "smartcn_stop")));
        Map<String, Object> properties = Map.of(
                "faqId", Map.of("type", "keyword"),
                "question", Map.of(
                        "type", "text",
                        "analyzer", "smartcs_zh",
                        "fields", Map.of("raw", Map.of("type", "keyword", "ignore_above", 512))),
                "answer", Map.of("type", "text", "analyzer", "smartcs_zh"),
                "keywords", Map.of(
                        "type", "text",
                        "analyzer", "smartcs_zh",
                        "fields", Map.of("raw", Map.of("type", "keyword"))),
                "category", Map.of("type", "keyword"),
                "status", Map.of("type", "keyword"),
                "priority", Map.of("type", "integer"),
                "contentHash", Map.of("type", "keyword"),
                "sourceUpdatedAt", Map.of("type", "date"));
        return Map.of(
                "settings", Map.of(
                        "number_of_shards", 1,
                        "number_of_replicas", 0,
                        "analysis", Map.of("analyzer", analyzer)),
                "mappings", Map.of("dynamic", "strict", "properties", properties));
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
