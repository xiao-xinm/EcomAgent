package com.smartcs.agent.workbench.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcs.agent.workbench.notification.NotificationEventDtos.NotificationEventRequest;
import com.smartcs.agent.workbench.notification.NotificationEventDtos.NotificationEventResult;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Notification HTTP client used by Workbench.
 *
 * 通知事件是辅助链路，调用失败不能影响工单审批和人工接管主流程。
 */
@Service
public class NotificationEventClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationEventClient.class);

    private final boolean enabled;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public NotificationEventClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${smartcs.notification.base-url:http://localhost:8085}") String baseUrl,
            @Value("${smartcs.notification.enabled:true}") boolean enabled) {
        this.enabled = enabled;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder
                .baseUrl(trimTrailingSlash(baseUrl))
                .requestFactory(requestFactory())
                .build();
    }

    public Optional<NotificationEventResult> publish(NotificationEventRequest request) {
        if (!enabled) {
            LOGGER.debug("Notification event skipped because client is disabled. eventType={} ticketId={}",
                    request.eventType(),
                    request.ticketId());
            return Optional.empty();
        }
        try {
            String body = restClient.post()
                    .uri("/api/notifications/events")
                    .body(request)
                    .retrieve()
                    .body(String.class);
            Optional<NotificationEventResult> result = parseResponse(body);
            result.ifPresent(eventResult -> LOGGER.info(
                    "Notification event published eventId={} status={} channel={} ticketId={} eventType={}",
                    eventResult.eventId(),
                    eventResult.status(),
                    eventResult.channel(),
                    request.ticketId(),
                    request.eventType()));
            return result;
        } catch (RestClientException | JsonProcessingException | IllegalArgumentException exception) {
            LOGGER.warn(
                    "Notification event publish failed, workbench operation will continue. traceId={} ticketId={} eventType={}",
                    request.traceId(),
                    request.ticketId(),
                    request.eventType(),
                    exception);
            return Optional.empty();
        }
    }

    private Optional<NotificationEventResult> parseResponse(String body) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(body);
        if (!"0000".equals(root.path("code").asText())) {
            LOGGER.warn(
                    "Notification service returned failure. code={} message={}",
                    root.path("code").asText(),
                    root.path("message").asText());
            return Optional.empty();
        }
        JsonNode data = root.get("data");
        if (data == null || data.isNull()) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.treeToValue(data, NotificationEventResult.class));
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofSeconds(2));
        return factory;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8085";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
