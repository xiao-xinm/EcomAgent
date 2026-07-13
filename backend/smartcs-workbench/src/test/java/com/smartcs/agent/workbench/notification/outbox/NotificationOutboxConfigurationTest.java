package com.smartcs.agent.workbench.notification.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartcs.agent.workbench.notification.NotificationEventClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NotificationOutboxConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    NotificationOutboxSchedulingConfiguration.class,
                    NotificationOutboxWorker.class)
            .withBean(NotificationOutboxRepository.class, this::repository)
            .withBean(NotificationEventClient.class, () -> mock(NotificationEventClient.class));

    @Test
    void outboxWorkerIsAbsentByDefault() {
        contextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(NotificationOutboxWorker.class));
    }

    @Test
    void enabledOutboxRequiresNotificationClientToBeEnabled() {
        contextRunner
                .withPropertyValues(
                        "smartcs.notification.outbox.enabled=true",
                        "smartcs.notification.enabled=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasStackTraceContaining("必须同时启用 Notification 客户端");
                });
    }

    @Test
    void enabledOutboxStartsWithNotificationClient() {
        contextRunner
                .withPropertyValues(
                        "smartcs.notification.outbox.enabled=true",
                        "smartcs.notification.enabled=true")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(NotificationOutboxWorker.class));
    }

    private NotificationOutboxRepository repository() {
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        when(repository.claimDue(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(20),
                        org.mockito.ArgumentMatchers.eq(5),
                        org.mockito.ArgumentMatchers.eq(120_000L)))
                .thenReturn(List.of());
        return repository;
    }
}
