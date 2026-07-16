package com.smartcs.agent.workbench.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.smartcs.agent.workbench.auth.WorkbenchIdentityResolver;
import com.smartcs.agent.workbench.auth.WorkbenchReadAccessGuard;
import com.smartcs.agent.workbench.controller.WorkbenchTicketEventController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

class WorkbenchTicketEventConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    WorkbenchTicketEventSchedulingConfiguration.class,
                    WorkbenchTicketChangeRepository.class,
                    WorkbenchTicketEventStream.class,
                    WorkbenchTicketEventController.class,
                    WorkbenchIdentityResolver.class,
                    WorkbenchReadAccessGuard.class)
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class));

    @Test
    void ticketEventStreamIsAbsentByDefault() {
        contextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(WorkbenchTicketEventStream.class)
                .doesNotHaveBean(WorkbenchTicketEventController.class));
    }

    @Test
    void ticketEventStreamStartsWhenExplicitlyEnabled() {
        contextRunner
                .withPropertyValues("smartcs.realtime.ticket-events.enabled=true")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(WorkbenchTicketEventStream.class)
                        .hasSingleBean(WorkbenchTicketEventController.class));
    }
}
