package com.smartcs.agent.workbench.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class UserMessageDeliveryPolicyTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(UserMessageDeliveryPolicy.class);

    @Test
    void directWriteIsTheSafeDefaultMode() {
        UserMessageDeliveryPolicy policy = new UserMessageDeliveryPolicy(true, false);

        assertThat(policy.directWriteEnabled()).isTrue();
        assertThat(policy.deliveryMode()).isEqualTo("DIRECT");
    }

    @Test
    void notificationModeRequiresOutbox() {
        assertThatThrownBy(() -> new UserMessageDeliveryPolicy(false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须同时启用通知 outbox");
    }

    @Test
    void notificationModeIsAllowedWithOutbox() {
        UserMessageDeliveryPolicy policy = new UserMessageDeliveryPolicy(false, true);

        assertThat(policy.directWriteEnabled()).isFalse();
        assertThat(policy.deliveryMode()).isEqualTo("NOTIFICATION");
    }

    @Test
    void invalidEnvironmentCombinationFailsApplicationStartup() {
        contextRunner
                .withPropertyValues(
                        "smartcs.notification.user-message-direct-write-enabled=false",
                        "smartcs.notification.outbox.enabled=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasStackTraceContaining("必须同时启用通知 outbox");
                });
    }

    @Test
    void completeMigrationEnvironmentStartsInNotificationMode() {
        contextRunner
                .withPropertyValues(
                        "smartcs.notification.user-message-direct-write-enabled=false",
                        "smartcs.notification.outbox.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(UserMessageDeliveryPolicy.class).deliveryMode())
                            .isEqualTo("NOTIFICATION");
                });
    }
}
