package com.smartcs.agent.knowledge.embedding;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 通过 DashScope OpenAI 兼容接口生成文本向量。
 *
 * <p>密钥只从环境配置读取，日志中不输出原文、密钥或完整向量。调用失败时返回 empty，
 * 由上层检索编排决定降级到 Elasticsearch 或现有关键词 FAQ。
 */
@Service
public class DashScopeEmbeddingClient implements EmbeddingClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(DashScopeEmbeddingClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final int dimensions;

    public DashScopeEmbeddingClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${smartcs.knowledge.embedding.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
                    String baseUrl,
            @Value("${smartcs.knowledge.embedding.api-key:}") String apiKey,
            @Value("${smartcs.knowledge.embedding.model:text-embedding-v4}") String model,
            @Value("${smartcs.knowledge.embedding.dimensions:1024}") int dimensions) {
        this.restClient = restClientBuilder.baseUrl(trimTrailingSlash(baseUrl)).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = requireText(model, "embedding model");
        if (dimensions <= 0) {
            throw new IllegalArgumentException("embedding dimensions 必须大于 0");
        }
        this.dimensions = dimensions;
    }

    @Override
    public Optional<EmbeddingResult> embed(String text) {
        if (apiKey.isBlank()) {
            LOGGER.warn("DashScope Embedding 未配置 DASHSCOPE_API_KEY，跳过语义召回");
            return Optional.empty();
        }
        if (text == null || text.isBlank()) {
            LOGGER.warn("DashScope Embedding 输入为空，跳过向量化");
            return Optional.empty();
        }

        try {
            LOGGER.info(
                    "DashScope Embedding 调用开始 model={} dimensions={} textLength={}",
                    model,
                    dimensions,
                    text.length());
            String body = restClient.post()
                    .uri("/embeddings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", model,
                            "input", text,
                            "dimensions", dimensions,
                            "encoding_format", "float"))
                    .retrieve()
                    .body(String.class);
            EmbeddingResult result = parseResponse(body);
            LOGGER.info(
                    "DashScope Embedding 调用完成 model={} dimensions={} promptTokens={}",
                    result.model(),
                    result.vector().size(),
                    result.promptTokens());
            return Optional.of(result);
        } catch (RestClientException | JsonProcessingException | IllegalArgumentException exception) {
            LOGGER.warn(
                    "DashScope Embedding 调用失败，语义召回将降级 model={} dimensions={} reason={}",
                    model,
                    dimensions,
                    exception.getMessage());
            return Optional.empty();
        }
    }

    private EmbeddingResult parseResponse(String body) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(requireText(body, "embedding response"));
        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new IllegalArgumentException("Embedding 响应缺少 data");
        }

        JsonNode embedding = data.get(0).path("embedding");
        if (!embedding.isArray() || embedding.size() != dimensions) {
            throw new IllegalArgumentException(
                    "Embedding 维度不匹配，expected=" + dimensions + ", actual=" + embedding.size());
        }

        List<Double> vector = new ArrayList<>(dimensions);
        for (JsonNode value : embedding) {
            double number = value.asDouble(Double.NaN);
            if (!Double.isFinite(number)) {
                throw new IllegalArgumentException("Embedding 包含非法数值");
            }
            vector.add(number);
        }
        String responseModel = root.path("model").asText(model);
        int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
        return new EmbeddingResult(responseModel, List.copyOf(vector), promptTokens);
    }

    private String trimTrailingSlash(String value) {
        String normalized = requireText(value, "embedding baseUrl");
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }
}
