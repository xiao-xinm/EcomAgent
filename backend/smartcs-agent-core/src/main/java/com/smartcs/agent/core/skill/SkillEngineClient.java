package com.smartcs.agent.core.skill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcs.agent.core.skill.SkillExecutionDtos.SkillExecutionRequest;
import com.smartcs.agent.core.skill.SkillExecutionDtos.SkillExecutionResult;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Skill Engine HTTP 客户端：Agent Core 只负责路由，真实技能执行交给 8082 服务。
 */
@Service
public class SkillEngineClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkillEngineClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public SkillEngineClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${smartcs.skill-engine.base-url:http://localhost:8082}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(trimTrailingSlash(baseUrl)).build();
        this.objectMapper = objectMapper;
    }

    public Optional<SkillExecutionResult> execute(SkillExecutionRequest request) {
        try {
            LOGGER.info(
                    "调用Skill Engine开始 traceId={} sessionId={} messageId={} skillId={} intent={} routeDecision={}",
                    request.traceId(),
                    request.sessionId(),
                    request.messageId(),
                    request.skillId(),
                    request.intent(),
                    request.routeDecision());
            String body = restClient.post()
                    .uri("/api/skills/execute")
                    .body(request)
                    .retrieve()
                    .body(String.class);
            Optional<SkillExecutionResult> result = parseResponse(body);
            result.ifPresent(skillResult -> LOGGER.info(
                    "调用Skill Engine成功 traceId={} executionId={} skillId={} status={}",
                    skillResult.traceId(),
                    skillResult.executionId(),
                    skillResult.skillId(),
                    skillResult.status()));
            return result;
        } catch (RestClientException | JsonProcessingException | IllegalArgumentException exception) {
            LOGGER.warn("Skill Engine call failed, fallback to local mock log. skillId={}, intent={}",
                    request.skillId(),
                    request.intent(),
                    exception);
            return Optional.empty();
        }
    }

    private Optional<SkillExecutionResult> parseResponse(String body) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(body);
        if (!"0000".equals(root.path("code").asText())) {
            LOGGER.warn("Skill Engine returned failure. code={}, message={}",
                    root.path("code").asText(),
                    root.path("message").asText());
            return Optional.empty();
        }
        JsonNode data = root.get("data");
        if (data == null || data.isNull()) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.treeToValue(data, SkillExecutionResult.class));
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8082";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
