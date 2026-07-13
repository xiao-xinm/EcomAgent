package com.smartcs.agent.notification.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class UserSessionNotificationDeliveryChannelTest {

    @Test
    void legacyEventIsAcknowledgedWithoutWritingDuplicateMessage() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UserSessionNotificationDeliveryChannel channel = channel(jdbcTemplate);

        channel.deliver(command(Map.of("messageRole", "SYSTEM")));

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void directEventIsAcknowledgedWithoutWritingDuplicateMessage() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UserSessionNotificationDeliveryChannel channel = channel(jdbcTemplate);

        channel.deliver(command(Map.of(
                "messageRole", "SYSTEM",
                "userMessageDeliveryMode", "DIRECT")));

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void notificationEventWritesUserMessageWithStableMessageId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UserSessionNotificationDeliveryChannel channel = channel(jdbcTemplate);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageRole", "HUMAN_AGENT");
        payload.put("userMessageDeliveryMode", "NOTIFICATION");
        payload.put("intent", "human.takeover");
        payload.put("riskLevel", "L2");
        payload.put("routeDecision", "HUMAN_TAKEOVER");
        payload.put("takeoverStatus", "IN_PROGRESS");

        channel.deliver(command(payload));
        channel.deliver(command(payload));

        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(2)).update(anyString(), arguments.capture());
        Object[] first = arguments.getAllValues().get(0);
        Object[] second = arguments.getAllValues().get(1);
        assertThat(first[0]).isEqualTo(second[0]).isEqualTo("m_cfbfdee8-5c93-34a6-b07b-207e7b34faec");
        assertThat(first)
                .containsSequence(
                        "trace_test",
                        "s_test",
                        "u1001",
                        "HUMAN_AGENT",
                        "人工客服已接入",
                        "human.takeover",
                        "L2",
                        "HUMAN_TAKEOVER");
        assertThat(first[9].toString())
                .contains("\"source\":\"notification\"")
                .contains("\"eventId\":\"ntf_test\"")
                .contains("\"takeoverStatus\":\"IN_PROGRESS\"");
    }

    @Test
    void unsupportedModeAndRoleAreRejected() {
        UserSessionNotificationDeliveryChannel channel = channel(mock(JdbcTemplate.class));

        assertThatThrownBy(() -> channel.deliver(command(Map.of(
                        "messageRole", "SYSTEM",
                        "userMessageDeliveryMode", "UNKNOWN"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的用户消息交付模式");
        assertThatThrownBy(() -> channel.deliver(command(Map.of(
                        "messageRole", "USER",
                        "userMessageDeliveryMode", "NOTIFICATION"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只允许 SYSTEM 或 HUMAN_AGENT");
    }

    @Test
    void enabledChannelRequiresRetryWorker() {
        assertThatThrownBy(() -> new UserSessionNotificationDeliveryChannel(
                        mock(JdbcTemplate.class),
                        new ObjectMapper(),
                        false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须同时启用 Notification 重试 worker");
    }

    private UserSessionNotificationDeliveryChannel channel(JdbcTemplate jdbcTemplate) {
        return new UserSessionNotificationDeliveryChannel(
                jdbcTemplate,
                new ObjectMapper().findAndRegisterModules(),
                true);
    }

    private NotificationDeliveryCommand command(Map<String, Object> payload) {
        return new NotificationDeliveryCommand(
                "ntf_test",
                "trace_test",
                "TAKEOVER_STARTED",
                "u1001",
                "s_test",
                "wo_test",
                "agent001",
                "USER_SESSION",
                "人工客服接入通知",
                "人工客服已接入",
                payload,
                Instant.parse("2026-07-13T08:00:00Z"),
                1);
    }
}
