package com.smartcs.agent.gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcs.agent.common.auth.AuthHeaders;
import com.smartcs.agent.common.domain.ChatRequest;
import com.smartcs.agent.common.domain.ChatMessageView;
import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.gateway.auth.GatewayIdentityResolver;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class ChatControllerTest {

    @Test
    void streamSessionEventsEmitsOnlyMessagesAfterLastMessageId() {
        ChatController controller = new ChatController(
                WebClient.builder().exchangeFunction(request -> Mono.just(jsonResponse(
                        request.url().getPath().endsWith("/messages")
                                ? messagesBody("u1001")
                                : sessionBody("u1001")))),
                "http://agent-core",
                new GatewayIdentityResolver());

        ServerSentEvent<Object> event = controller
                .streamSessionEvents(HttpHeaders.EMPTY, "s_test", "m1", 100, 1000)
                .filter(candidate -> "message.created".equals(candidate.event()))
                .blockFirst(Duration.ofSeconds(2));

        assertThat(event).isNotNull();
        assertThat(event.id()).isEqualTo("m2");
        assertThat(event.data()).isInstanceOf(ChatMessageView.class);
        ChatMessageView message = (ChatMessageView) event.data();
        assertThat(message.messageId()).isEqualTo("m2");
        assertThat(message.role()).isEqualTo("HUMAN_AGENT");
    }

    @Test
    void getSessionRejectsSessionOwnedByAnotherUser() {
        ChatController controller = new ChatController(
                WebClient.builder().exchangeFunction(request -> Mono.just(jsonResponse(sessionBody("u2002")))),
                "http://agent-core",
                new GatewayIdentityResolver());
        HttpHeaders headers = new HttpHeaders();
        headers.add(AuthHeaders.DEV_USER_ID, "u1001");

        ApiResponse<?> response = controller.getSession(headers, "s_test").block(Duration.ofSeconds(2));

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("1003");
    }

    @Test
    void listMessagesRejectsSessionOwnedByAnotherUserBeforeLoadingMessages() {
        AtomicInteger messageFetchCount = new AtomicInteger();
        ChatController controller = new ChatController(
                WebClient.builder().exchangeFunction(request -> {
                    if (request.url().getPath().endsWith("/messages")) {
                        messageFetchCount.incrementAndGet();
                        return Mono.just(jsonResponse(messagesBody("u2002")));
                    }
                    return Mono.just(jsonResponse(sessionBody("u2002")));
                }),
                "http://agent-core",
                new GatewayIdentityResolver());
        HttpHeaders headers = new HttpHeaders();
        headers.add(AuthHeaders.DEV_USER_ID, "u1001");

        ApiResponse<?> response = controller.listMessages(headers, "s_test", 100).block(Duration.ofSeconds(2));

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("1003");
        assertThat(messageFetchCount.get()).isZero();
    }

    @Test
    void sendMessageRejectsExistingSessionOwnedByAnotherUser() {
        AtomicInteger postCount = new AtomicInteger();
        ChatController controller = new ChatController(
                WebClient.builder().exchangeFunction(request -> {
                    if (request.method() == HttpMethod.POST) {
                        postCount.incrementAndGet();
                        return Mono.just(jsonResponse(agentReplyBody()));
                    }
                    return Mono.just(jsonResponse(sessionBody("u2002")));
                }),
                "http://agent-core",
                new GatewayIdentityResolver());
        HttpHeaders headers = new HttpHeaders();
        headers.add(AuthHeaders.DEV_USER_ID, "u1001");

        ApiResponse<?> response = controller.sendMessage(
                        headers,
                        new ChatRequest("trace_send", "s_test", "u1001", "h5", "你好", Map.of()))
                .block(Duration.ofSeconds(2));

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("1003");
        assertThat(postCount.get()).isZero();
    }

    @Test
    void streamSessionEventsRejectsSessionOwnedByAnotherUser() {
        ChatController controller = new ChatController(
                WebClient.builder().exchangeFunction(request -> Mono.just(jsonResponse(sessionBody("u2002")))),
                "http://agent-core",
                new GatewayIdentityResolver());
        HttpHeaders headers = new HttpHeaders();
        headers.add(AuthHeaders.DEV_USER_ID, "u1001");

        ServerSentEvent<Object> event = controller
                .streamSessionEvents(headers, "s_test", null, 100, 1000)
                .blockFirst(Duration.ofSeconds(2));

        assertThat(event).isNotNull();
        assertThat(event.event()).isEqualTo("auth.error");
        assertThat(event.data()).isInstanceOf(ApiResponse.class);
        ApiResponse<?> response = (ApiResponse<?>) event.data();
        assertThat(response.code()).isEqualTo("1003");
    }

    @Test
    void identityResolverPrefersDevHeaderUserIdOverLegacyBody() {
        GatewayIdentityResolver resolver = new GatewayIdentityResolver();
        HttpHeaders headers = new HttpHeaders();
        headers.add(AuthHeaders.DEV_USER_ID, "u2002");

        var principal = resolver.resolveCustomer(headers, "u1001").orElseThrow();

        assertThat(principal.principalId()).isEqualTo("u2002");
        assertThat(principal.principalType().name()).isEqualTo("CUSTOMER");
        assertThat(principal.roles()).containsExactly("CUSTOMER");
        assertThat(principal.authSource().name()).isEqualTo("DEV_HEADER");
    }

    private static ClientResponse jsonResponse(String body) {
        return ClientResponse
                .create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private static String sessionBody(String userId) {
        return """
                {
                  "code": "0000",
                  "message": "success",
                  "data": {
                    "sessionId": "s_test",
                    "traceId": "trace_session",
                    "userId": "%s",
                    "channel": "h5",
                    "status": "ACTIVE",
                    "dialogState": "ACTIVE",
                    "currentIntent": "order.query",
                    "slots": {},
                    "contextSnapshot": {},
                    "lastMessageAt": "2026-07-05T01:00:01Z",
                    "createdAt": "2026-07-05T01:00:00Z",
                    "updatedAt": "2026-07-05T01:00:01Z",
                    "closedAt": null
                  },
                  "traceId": "trace_session",
                  "metadata": {},
                  "timestamp": "2026-07-05T01:00:02Z"
                }
                """.formatted(userId);
    }

    private static String messagesBody(String userId) {
        return """
                {
                  "code": "0000",
                  "message": "success",
                  "data": [
                    {
                      "messageId": "m1",
                      "traceId": "trace_sse",
                      "sessionId": "s_test",
                      "userId": "%s",
                      "role": "AGENT",
                      "messageType": "TEXT",
                      "content": "旧消息",
                      "attachments": [],
                      "quickActions": [],
                      "intent": null,
                      "riskLevel": null,
                      "routeDecision": null,
                      "metadata": {},
                      "createdAt": "2026-07-05T01:00:00Z"
                    },
                    {
                      "messageId": "m2",
                      "traceId": "trace_sse",
                      "sessionId": "s_test",
                      "userId": "%s",
                      "role": "HUMAN_AGENT",
                      "messageType": "TEXT",
                      "content": "新消息",
                      "attachments": [],
                      "quickActions": [],
                      "intent": null,
                      "riskLevel": null,
                      "routeDecision": null,
                      "metadata": {},
                      "createdAt": "2026-07-05T01:00:01Z"
                    }
                  ],
                  "traceId": "trace_sse",
                  "metadata": {},
                  "timestamp": "2026-07-05T01:00:02Z"
                }
                """.formatted(userId, userId);
    }

    private static String agentReplyBody() {
        return """
                {
                  "code": "0000",
                  "message": "success",
                  "data": {
                    "replyId": "r_test",
                    "traceId": "trace_send",
                    "sessionId": "s_test",
                    "messageType": "TEXT",
                    "content": "ok",
                    "quickActions": [],
                    "riskLevel": "L0",
                    "routeDecision": "AUTO_REPLY",
                    "ticketId": null,
                    "metadata": {},
                    "createdAt": "2026-07-05T01:00:02Z"
                  },
                  "traceId": "trace_send",
                  "metadata": {},
                  "timestamp": "2026-07-05T01:00:02Z"
                }
                """;
    }
}
