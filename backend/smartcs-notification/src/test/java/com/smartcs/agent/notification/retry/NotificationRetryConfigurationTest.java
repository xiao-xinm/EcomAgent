package com.smartcs.agent.notification.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartcs.agent.notification.delivery.NotificationDeliveryChannel;
import com.smartcs.agent.notification.delivery.UserSessionNotificationDeliveryChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

class NotificationRetryConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    NotificationRetrySchedulingConfiguration.class,
                    NotificationRetryWorker.class)
            .withBean(NotificationRetryRepository.class, () -> mock(NotificationRetryRepository.class));

    @Test
    void retryWorkerIsAbsentByDefault() {
        contextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(NotificationRetryWorker.class));
    }

    @Test
    void enabledRetryRequiresAtLeastOneDeliveryChannel() {
        contextRunner
                .withPropertyValues("smartcs.notification.retry.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasStackTraceContaining("至少需要注册一个通知投递通道");
                });
    }

    @Test
    void enabledRetryStartsWhenDeliveryChannelExists() {
        contextRunner
                .withPropertyValues("smartcs.notification.retry.enabled=true")
                .withBean(NotificationDeliveryChannel.class, () -> deliveryChannel("USER_SESSION"))
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(NotificationRetryWorker.class));
    }

    @Test
    void enabledUserSessionChannelRequiresRetryWorker() {
        contextRunner
                .withUserConfiguration(UserSessionNotificationDeliveryChannel.class)
                .withPropertyValues("smartcs.notification.channel.user-session.enabled=true")
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasStackTraceContaining("必须同时启用 Notification 重试 worker");
                });
    }

    @Test
    void userSessionChannelAndRetryWorkerStartTogether() {
        contextRunner
                .withUserConfiguration(UserSessionNotificationDeliveryChannel.class)
                .withPropertyValues(
                        "smartcs.notification.channel.user-session.enabled=true",
                        "smartcs.notification.retry.enabled=true")
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(UserSessionNotificationDeliveryChannel.class)
                        .hasSingleBean(NotificationRetryWorker.class));
    }

    private NotificationDeliveryChannel deliveryChannel(String name) {
        NotificationDeliveryChannel channel = mock(NotificationDeliveryChannel.class);
        when(channel.channel()).thenReturn(name);
        return channel;
    }
}
