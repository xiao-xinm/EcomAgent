package com.smartcs.agent.core.knowledge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcs.agent.core.knowledge.KnowledgeFaqDtos.FaqQueryRequest;
import com.smartcs.agent.core.knowledge.KnowledgeFaqDtos.FaqQueryResult;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Knowledge service HTTP client.
 *
 * 当前只调用 FAQ 关键词检索接口。调用失败时返回 empty，让 Agent Core 使用本地兜底回复，
 * 保证聊天主链路不会因为 Knowledge 服务短暂不可用而中断。
 */
@Service
public class KnowledgeFaqClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeFaqClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public KnowledgeFaqClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${smartcs.knowledge.base-url:http://localhost:8084}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(trimTrailingSlash(baseUrl)).build();
        this.objectMapper = objectMapper;
    }

    public Optional<FaqQueryResult> query(FaqQueryRequest request) {
        try {
            LOGGER.info(
                    "Knowledge FAQ call started traceId={} sessionId={} userId={} questionLength={}",
                    request.traceId(),
                    request.sessionId(),
                    request.userId(),
                    request.question() == null ? 0 : request.question().length());
            String body = restClient.post()
                    .uri("/api/knowledge/faq/query")
                    .body(request)
                    .retrieve()
                    .body(String.class);
            Optional<FaqQueryResult> result = parseResponse(body);
            result.ifPresent(answer -> LOGGER.info(
                    "Knowledge FAQ call succeeded traceId={} answerId={} matched={} confidence={}",
                    request.traceId(),
                    answer.answerId(),
                    answer.matched(),
                    answer.confidence()));
            return result;
        } catch (RestClientException | JsonProcessingException | IllegalArgumentException exception) {
            LOGGER.warn(
                    "Knowledge FAQ call failed, fallback to local reply. traceId={} sessionId={}",
                    request.traceId(),
                    request.sessionId(),
                    exception);
            return Optional.empty();
        }
    }

    private Optional<FaqQueryResult> parseResponse(String body) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(body);
        if (!"0000".equals(root.path("code").asText())) {
            LOGGER.warn(
                    "Knowledge FAQ returned failure. code={} message={}",
                    root.path("code").asText(),
                    root.path("message").asText());
            return Optional.empty();
        }
        JsonNode data = root.get("data");
        if (data == null || data.isNull()) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.treeToValue(data, FaqQueryResult.class));
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8084";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
