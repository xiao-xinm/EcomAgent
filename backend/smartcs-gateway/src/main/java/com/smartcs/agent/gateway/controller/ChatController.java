package com.smartcs.agent.gateway.controller;

import com.smartcs.agent.common.domain.AgentReply;
import com.smartcs.agent.common.domain.ChatActionRequest;
import com.smartcs.agent.common.domain.ChatMessageView;
import com.smartcs.agent.common.domain.ChatRequest;
import com.smartcs.agent.common.domain.ChatSessionView;
import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.common.enums.ErrorCode;
import com.smartcs.agent.common.util.TraceIds;
import com.smartcs.agent.common.auth.AuthHeaders;
import com.smartcs.agent.common.auth.AuthenticatedPrincipal;
import com.smartcs.agent.common.auth.JwtPrincipalException;
import com.smartcs.agent.gateway.auth.GatewayIdentityResolver;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 聊天接入入口：负责补齐 trace/session/channel 后转发给 Agent Core。
 */
@RestController
public class ChatController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatController.class);
    private static final ParameterizedTypeReference<ApiResponse<AgentReply>> AGENT_REPLY_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<ApiResponse<ChatSessionView>> CHAT_SESSION_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<ApiResponse<List<ChatMessageView>>> CHAT_MESSAGES_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final String APPLICATION_JSON_UTF8 = "application/json;charset=UTF-8";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final long MIN_SSE_INTERVAL_MS = 1000L;
    private static final long MAX_SSE_INTERVAL_MS = 15000L;
    private static final int DEFAULT_MESSAGE_LIMIT = 100;
    private static final int MAX_MESSAGE_LIMIT = 200;

    private final WebClient agentCoreClient;
    private final GatewayIdentityResolver identityResolver;

    public ChatController(
            WebClient.Builder webClientBuilder,
            @Value("${smartcs.agent-core.base-url:http://localhost:8081}") String agentCoreBaseUrl,
            GatewayIdentityResolver identityResolver) {
        this.agentCoreClient = webClientBuilder.baseUrl(agentCoreBaseUrl).build();
        this.identityResolver = identityResolver;
    }

    @PostMapping(
            value = "/api/chat/messages",
            consumes = APPLICATION_JSON_UTF8,
            produces = APPLICATION_JSON_UTF8)
    public Mono<ApiResponse<AgentReply>> sendMessage(
            @RequestHeader HttpHeaders headers,
            @Valid @RequestBody ChatRequest request) {
        Optional<AuthenticatedPrincipal> principal;
        try {
            principal = identityResolver.resolveCustomer(headers, request.userId());
        } catch (JwtPrincipalException exception) {
            return unauthorizedResponse(requestTraceId(request.traceId()), exception);
        }
        ChatRequest normalized = normalize(request, principal);
        LOGGER.info(
                "Gateway收到聊天请求 traceId={} sessionId={} userId={} channel={} authSource={} contentLength={}",
                normalized.traceId(),
                normalized.sessionId(),
                normalized.userId(),
                normalized.channel(),
                principal.map(value -> value.authSource().name()).orElse("NONE"),
                contentLength(normalized.content()));
        return agentCoreClient.post()
                .uri("/api/agent/chat")
                .header(REQUEST_ID_HEADER, normalized.traceId())
                .headers(outgoingHeaders -> applyIdentityHeaders(outgoingHeaders, principal))
                .contentType(MediaType.parseMediaType(APPLICATION_JSON_UTF8))
                .accept(MediaType.parseMediaType(APPLICATION_JSON_UTF8))
                .bodyValue(normalized)
                .retrieve()
                .bodyToMono(AGENT_REPLY_TYPE)
                .doOnNext(response -> logAgentCoreResponse(normalized, response))
                .onErrorResume(error -> {
                    LOGGER.warn(
                            "Gateway转发Agent Core失败 traceId={} sessionId={} userId={}",
                            normalized.traceId(),
                            normalized.sessionId(),
                            normalized.userId(),
                            error);
                    return Mono.just(ApiResponse.failure(ErrorCode.INTERNAL_ERROR, normalized.traceId()));
                });
    }

    @PostMapping(
            value = "/api/chat/actions",
            consumes = APPLICATION_JSON_UTF8,
            produces = APPLICATION_JSON_UTF8)
    public Mono<ApiResponse<AgentReply>> handleAction(
            @RequestHeader HttpHeaders headers,
            @Valid @RequestBody ChatActionRequest request) {
        Optional<AuthenticatedPrincipal> principal;
        try {
            principal = identityResolver.resolveCustomer(headers, request.userId());
        } catch (JwtPrincipalException exception) {
            return unauthorizedResponse(requestTraceId(request.traceId()), exception);
        }
        ChatActionRequest normalized = normalizeAction(request, principal);
        LOGGER.info(
                "Gateway收到聊天动作 traceId={} sessionId={} userId={} channel={} authSource={} actionType={} actionId={}",
                normalized.traceId(),
                normalized.sessionId(),
                normalized.userId(),
                normalized.channel(),
                principal.map(value -> value.authSource().name()).orElse("NONE"),
                normalized.actionType(),
                normalized.actionId());
        return agentCoreClient.post()
                .uri("/api/agent/actions")
                .header(REQUEST_ID_HEADER, normalized.traceId())
                .headers(outgoingHeaders -> applyIdentityHeaders(outgoingHeaders, principal))
                .contentType(MediaType.parseMediaType(APPLICATION_JSON_UTF8))
                .accept(MediaType.parseMediaType(APPLICATION_JSON_UTF8))
                .bodyValue(normalized)
                .retrieve()
                .bodyToMono(AGENT_REPLY_TYPE)
                .doOnNext(response -> logAgentCoreActionResponse(normalized, response))
                .onErrorResume(error -> {
                    LOGGER.warn(
                            "Gateway转发聊天动作失败 traceId={} sessionId={} userId={} actionType={}",
                            normalized.traceId(),
                            normalized.sessionId(),
                            normalized.userId(),
                            normalized.actionType(),
                            error);
                    return Mono.just(ApiResponse.failure(ErrorCode.INTERNAL_ERROR, normalized.traceId()));
                });
    }

    @GetMapping(value = "/api/chat/sessions/{sessionId}", produces = APPLICATION_JSON_UTF8)
    public Mono<ApiResponse<ChatSessionView>> getSession(
            @RequestHeader HttpHeaders headers,
            @PathVariable String sessionId) {
        String traceId = TraceIds.newTraceId();
        Optional<AuthenticatedPrincipal> principal;
        try {
            principal = identityResolver.resolveCustomer(headers, null);
        } catch (JwtPrincipalException exception) {
            return unauthorizedResponse(traceId, exception);
        }
        LOGGER.info(
                "Gateway查询会话状态 traceId={} sessionId={} principalId={} authSource={}",
                traceId,
                sessionId,
                principal.map(AuthenticatedPrincipal::principalId).orElse("NONE"),
                principal.map(value -> value.authSource().name()).orElse("NONE"));
        return agentCoreClient.get()
                .uri("/api/agent/sessions/{sessionId}", sessionId)
                .header(REQUEST_ID_HEADER, traceId)
                .headers(outgoingHeaders -> applyIdentityHeaders(outgoingHeaders, principal))
                .accept(MediaType.parseMediaType(APPLICATION_JSON_UTF8))
                .retrieve()
                .bodyToMono(CHAT_SESSION_TYPE)
                .doOnNext(response -> LOGGER.info(
                        "Gateway完成会话状态查询 traceId={} sessionId={} code={}",
                        traceId,
                        sessionId,
                        response.code()))
                .onErrorResume(error -> {
                    LOGGER.warn("Gateway查询会话状态失败 traceId={} sessionId={}", traceId, sessionId, error);
                    return Mono.just(ApiResponse.<ChatSessionView>failure(ErrorCode.INTERNAL_ERROR, traceId));
                });
    }

    @GetMapping(value = "/api/chat/sessions/{sessionId}/messages", produces = APPLICATION_JSON_UTF8)
    public Mono<ApiResponse<List<ChatMessageView>>> listMessages(
            @RequestHeader HttpHeaders headers,
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "100") int limit) {
        String traceId = TraceIds.newTraceId();
        int normalizedLimit = normalizeLimit(limit);
        Optional<AuthenticatedPrincipal> principal;
        try {
            principal = identityResolver.resolveCustomer(headers, null);
        } catch (JwtPrincipalException exception) {
            return unauthorizedResponse(traceId, exception);
        }
        LOGGER.info(
                "Gateway查询会话消息 traceId={} sessionId={} principalId={} authSource={} limit={}",
                traceId,
                sessionId,
                principal.map(AuthenticatedPrincipal::principalId).orElse("NONE"),
                principal.map(value -> value.authSource().name()).orElse("NONE"),
                normalizedLimit);
        return fetchSessionMessages(sessionId, normalizedLimit, traceId, principal)
                .doOnNext(response -> LOGGER.info(
                        "Gateway完成会话消息查询 traceId={} sessionId={} code={}",
                        traceId,
                        sessionId,
                        response.code()))
                .onErrorResume(error -> {
                    LOGGER.warn("Gateway查询会话消息失败 traceId={} sessionId={}", traceId, sessionId, error);
                    return Mono.just(ApiResponse.<List<ChatMessageView>>failure(ErrorCode.INTERNAL_ERROR, traceId));
                });
    }

    @GetMapping(value = "/api/chat/sessions/{sessionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> streamSessionEvents(
            @RequestHeader HttpHeaders headers,
            @PathVariable String sessionId,
            @RequestParam(required = false) String lastMessageId,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "3000") long intervalMs) {
        String streamId = TraceIds.newTraceId();
        int normalizedLimit = normalizeLimit(limit);
        long normalizedIntervalMs = normalizeSseInterval(intervalMs);
        Optional<AuthenticatedPrincipal> principal;
        try {
            principal = identityResolver.resolveCustomer(headers, null);
        } catch (JwtPrincipalException exception) {
            return unauthorizedStream(streamId, exception);
        }
        Set<String> emittedMessageIds = ConcurrentHashMap.newKeySet();
        AtomicBoolean firstFetch = new AtomicBoolean(true);
        LOGGER.info(
                "Gateway打开会话SSE streamId={} sessionId={} principalId={} authSource={} lastMessageId={} limit={} intervalMs={}",
                streamId,
                sessionId,
                principal.map(AuthenticatedPrincipal::principalId).orElse("NONE"),
                principal.map(value -> value.authSource().name()).orElse("NONE"),
                lastMessageId,
                normalizedLimit,
                normalizedIntervalMs);

        Flux<ServerSentEvent<Object>> messageEvents = Flux
                .interval(Duration.ZERO, Duration.ofMillis(normalizedIntervalMs))
                .concatMap(tick -> fetchSessionMessages(sessionId, normalizedLimit, TraceIds.newTraceId(), principal)
                        .map(response -> unseenMessages(response, emittedMessageIds, lastMessageId, firstFetch))
                        .onErrorResume(error -> {
                            LOGGER.warn("Gateway SSE拉取消息失败 streamId={} sessionId={}", streamId, sessionId, error);
                            return Mono.just(List.of());
                        }))
                .flatMapIterable(messages -> messages)
                .map(message -> ServerSentEvent.builder((Object) message)
                        .id(message.messageId())
                        .event("message.created")
                        .build());
        Flux<ServerSentEvent<Object>> heartbeatEvents = Flux
                .interval(Duration.ofSeconds(15))
                .map(tick -> ServerSentEvent.builder((Object) Map.of(
                                "sessionId", sessionId,
                                "timestamp", Instant.now().toString()))
                        .event("heartbeat")
                        .build());

        return Flux.merge(messageEvents, heartbeatEvents)
                .doFinally(signal -> LOGGER.info(
                        "Gateway关闭会话SSE streamId={} sessionId={} signal={}",
                        streamId,
                        sessionId,
                        signal));
    }

    private Mono<ApiResponse<List<ChatMessageView>>> fetchSessionMessages(
            String sessionId,
            int limit,
            String traceId,
            Optional<AuthenticatedPrincipal> principal) {
        return agentCoreClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/agent/sessions/{sessionId}/messages")
                        .queryParam("limit", limit)
                        .build(sessionId))
                .header(REQUEST_ID_HEADER, traceId)
                .headers(outgoingHeaders -> applyIdentityHeaders(outgoingHeaders, principal))
                .accept(MediaType.parseMediaType(APPLICATION_JSON_UTF8))
                .retrieve()
                .bodyToMono(CHAT_MESSAGES_TYPE);
    }

    private <T> Mono<ApiResponse<T>> unauthorizedResponse(String traceId, JwtPrincipalException exception) {
        LOGGER.warn("Gateway拒绝无效Bearer Token traceId={} reason={}", traceId, exception.getMessage());
        return Mono.just(ApiResponse.failure(ErrorCode.UNAUTHORIZED, traceId));
    }

    private Flux<ServerSentEvent<Object>> unauthorizedStream(String streamId, JwtPrincipalException exception) {
        LOGGER.warn("Gateway拒绝无效SSE Bearer Token streamId={} reason={}", streamId, exception.getMessage());
        return Flux.just(ServerSentEvent.builder((Object) ApiResponse.failure(ErrorCode.UNAUTHORIZED, streamId))
                .event("auth.error")
                .build());
    }

    private List<ChatMessageView> unseenMessages(
            ApiResponse<List<ChatMessageView>> response,
            Set<String> emittedMessageIds,
            String lastMessageId,
            AtomicBoolean firstFetch) {
        if (!ErrorCode.SUCCESS.code().equals(response.code()) || response.data() == null) {
            return List.of();
        }
        List<ChatMessageView> messages = response.data();
        if (firstFetch.getAndSet(false) && hasText(lastMessageId)) {
            return messagesAfterLastMessage(messages, emittedMessageIds, lastMessageId);
        }
        return messages.stream()
                .filter(message -> emittedMessageIds.add(message.messageId()))
                .toList();
    }

    private List<ChatMessageView> messagesAfterLastMessage(
            List<ChatMessageView> messages,
            Set<String> emittedMessageIds,
            String lastMessageId) {
        List<ChatMessageView> result = new ArrayList<>();
        boolean afterLastMessage = false;
        for (ChatMessageView message : messages) {
            if (!afterLastMessage) {
                emittedMessageIds.add(message.messageId());
                if (lastMessageId.equals(message.messageId())) {
                    afterLastMessage = true;
                }
                continue;
            }
            if (emittedMessageIds.add(message.messageId())) {
                result.add(message);
            }
        }
        return result;
    }

    private ChatRequest normalize(ChatRequest request, Optional<AuthenticatedPrincipal> principal) {
        // 前端可以不传 traceId/sessionId；Gateway 统一补齐，方便后端全链路排查。
        String traceId = hasText(request.traceId()) ? request.traceId() : TraceIds.newTraceId();
        String sessionId = hasText(request.sessionId()) ? request.sessionId() : "s_" + UUID.randomUUID();
        String channel = hasText(request.channel()) ? request.channel() : "h5";
        Map<String, Object> metadata = request.metadata() == null ? Map.of() : request.metadata();
        String userId = principal.map(AuthenticatedPrincipal::principalId).orElse(request.userId());
        return new ChatRequest(traceId, sessionId, userId, channel, request.content(), metadata);
    }

    private ChatActionRequest normalizeAction(ChatActionRequest request, Optional<AuthenticatedPrincipal> principal) {
        // 动作请求必须沿用原 sessionId，traceId 可由 Gateway 补齐。
        String traceId = hasText(request.traceId()) ? request.traceId() : TraceIds.newTraceId();
        String channel = hasText(request.channel()) ? request.channel() : "h5";
        Map<String, Object> payload = request.payload() == null ? Map.of() : request.payload();
        Map<String, Object> metadata = request.metadata() == null ? Map.of() : request.metadata();
        String userId = principal.map(AuthenticatedPrincipal::principalId).orElse(request.userId());
        return new ChatActionRequest(
                traceId,
                request.sessionId(),
                userId,
                channel,
                request.actionId(),
                request.actionType(),
                request.content(),
                payload,
                metadata);
    }

    private void applyIdentityHeaders(HttpHeaders headers, Optional<AuthenticatedPrincipal> principal) {
        principal.ifPresent(value -> {
            headers.set(AuthHeaders.PRINCIPAL_ID, value.principalId());
            headers.set(AuthHeaders.PRINCIPAL_TYPE, value.principalType().name());
            headers.set(AuthHeaders.ROLES, value.rolesCsv());
            headers.set(AuthHeaders.PERMISSIONS, value.permissionsCsv());
            headers.set(AuthHeaders.AUTH_SOURCE, value.authSource().name());
        });
    }

    private void logAgentCoreResponse(ChatRequest request, ApiResponse<AgentReply> response) {
        AgentReply reply = response.data();
        if (reply == null) {
            LOGGER.warn(
                    "Gateway收到Agent Core空响应 traceId={} sessionId={} code={} message={}",
                    request.traceId(),
                    request.sessionId(),
                    response.code(),
                    response.message());
            return;
        }
        LOGGER.info(
                "Gateway完成聊天转发 traceId={} sessionId={} code={} routeDecision={} riskLevel={} ticketId={}",
                request.traceId(),
                request.sessionId(),
                response.code(),
                reply.routeDecision(),
                reply.riskLevel(),
                reply.ticketId());
    }

    private void logAgentCoreActionResponse(ChatActionRequest request, ApiResponse<AgentReply> response) {
        AgentReply reply = response.data();
        if (reply == null) {
            LOGGER.warn(
                    "Gateway收到Agent Core动作空响应 traceId={} sessionId={} code={} message={}",
                    request.traceId(),
                    request.sessionId(),
                    response.code(),
                    response.message());
            return;
        }
        LOGGER.info(
                "Gateway完成聊天动作转发 traceId={} sessionId={} code={} actionType={} routeDecision={} riskLevel={}",
                request.traceId(),
                request.sessionId(),
                response.code(),
                request.actionType(),
                reply.routeDecision(),
                reply.riskLevel());
    }

    private int contentLength(String content) {
        return content == null ? 0 : content.length();
    }

    private String requestTraceId(String traceId) {
        return hasText(traceId) ? traceId : TraceIds.newTraceId();
    }

    private int normalizeLimit(int limit) {
        return Math.min(MAX_MESSAGE_LIMIT, Math.max(1, limit <= 0 ? DEFAULT_MESSAGE_LIMIT : limit));
    }

    private long normalizeSseInterval(long intervalMs) {
        return Math.min(MAX_SSE_INTERVAL_MS, Math.max(MIN_SSE_INTERVAL_MS, intervalMs));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
