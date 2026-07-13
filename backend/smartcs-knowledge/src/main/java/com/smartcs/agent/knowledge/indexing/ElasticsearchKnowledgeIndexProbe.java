package com.smartcs.agent.knowledge.indexing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Elasticsearch ACTIVE FAQ 文档计数探针。 */
@Service
@ConditionalOnProperty(name = "smartcs.knowledge.retrieval.mode", havingValue = "hybrid")
public class ElasticsearchKnowledgeIndexProbe implements KnowledgeIndexProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchKnowledgeIndexProbe.class);
    private static final String NAME = "elasticsearch";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String index;

    public ElasticsearchKnowledgeIndexProbe(
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
    public String name() {
        return NAME;
    }

    @Override
    public ProbeResult probe() {
        try {
            String body = restClient.post()
                    .uri("/{index}/_count", index)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("query", Map.of("term", Map.of("status", "ACTIVE"))))
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(requireText(body, "elasticsearch count response"));
            JsonNode countNode = root.path("count");
            if (!countNode.canConvertToLong()) {
                throw new IllegalArgumentException("Elasticsearch count 响应缺少有效 count");
            }
            return ProbeResult.ready(NAME, countNode.asLong());
        } catch (RestClientException | JsonProcessingException | IllegalArgumentException exception) {
            LOGGER.warn("Elasticsearch FAQ 索引状态检查失败 index={} reason={}", index, exception.getMessage());
            return ProbeResult.unavailable(NAME, safeMessage(exception));
        }
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

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
