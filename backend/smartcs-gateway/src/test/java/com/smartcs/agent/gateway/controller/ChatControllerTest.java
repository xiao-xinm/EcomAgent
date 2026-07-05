package com.smartcs.agent.gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartcs.agent.common.auth.AuthHeaders;
import com.smartcs.agent.common.domain.ChatMessageView;
import com.smartcs.agent.gateway.auth.GatewayIdentityResolver;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
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
                WebClient.builder().exchangeFunction(request -> Mono.just(ClientResponse
                        .create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("""
                                {
                                  "code": "0000",
                                  "message": "success",
                                  "data": [
                                    {
                                      "messageId": "m1",
                                      "traceId": "trace_sse",
                                      "sessionId": "s_test",
                                      "userId": "u1001",
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
                                      "userId": "u1001",
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
                                """)
                        .build())),
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
}
