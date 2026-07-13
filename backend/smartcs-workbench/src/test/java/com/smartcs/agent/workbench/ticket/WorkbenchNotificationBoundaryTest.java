package com.smartcs.agent.workbench.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcs.agent.workbench.notification.NotificationEventDtos.NotificationEventRequest;
import com.smartcs.agent.workbench.notification.UserMessageDeliveryPolicy;
import com.smartcs.agent.workbench.notification.outbox.NotificationOutboxPublisher;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.WorkOrderView;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class WorkbenchNotificationBoundaryTest {

    @Test
    void directModeWritesMessageAndMarksEventAsAlreadyDelivered() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NotificationOutboxPublisher publisher = mock(NotificationOutboxPublisher.class);
        WorkbenchTicketService service = new WorkbenchTicketService(
                jdbcTemplate,
                new ObjectMapper().findAndRegisterModules(),
                publisher,
                new UserMessageDeliveryPolicy(true, false));
        WorkOrderView ticket = ticket();

        ReflectionTestUtils.invokeMethod(
                service,
                "publishUserServiceEvent",
                ticket,
                "HUMAN_AGENT",
                "人工客服已接入",
                "agent001",
                "TAKEOVER_STARTED",
                "人工客服接入通知",
                Map.of("takeoverStatus", "IN_PROGRESS"));

        verify(jdbcTemplate).update(anyString(), any(Object[].class));
        ArgumentCaptor<NotificationEventRequest> captor = ArgumentCaptor.forClass(NotificationEventRequest.class);
        verify(publisher).publish(captor.capture());
        NotificationEventRequest event = captor.getValue();
        assertThat(event.eventId()).startsWith("ntf_");
        assertThat(event.traceId()).isEqualTo("trace_test");
        assertThat(event.sessionId()).isEqualTo("s_test");
        assertThat(event.ticketId()).isEqualTo("wo_test");
        assertThat(event.recipientUserId()).isEqualTo("u1001");
        assertThat(event.operatorId()).isEqualTo("agent001");
        assertThat(event.channel()).isEqualTo("USER_SESSION");
        assertThat(event.payload())
                .containsEntry("intent", "human.takeover")
                .containsEntry("riskLevel", "L2")
                .containsEntry("routeDecision", "HUMAN_TAKEOVER")
                .containsEntry("takeoverStatus", "IN_PROGRESS")
                .containsEntry("messageRole", "HUMAN_AGENT")
                .containsEntry("userMessageDeliveryMode", "DIRECT");
    }

    @Test
    void notificationModeDoesNotWriteMessageAndCarriesDeliveryContract() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NotificationOutboxPublisher publisher = mock(NotificationOutboxPublisher.class);
        WorkbenchTicketService service = new WorkbenchTicketService(
                jdbcTemplate,
                new ObjectMapper().findAndRegisterModules(),
                publisher,
                new UserMessageDeliveryPolicy(false, true));

        ReflectionTestUtils.invokeMethod(
                service,
                "publishUserServiceEvent",
                ticket(),
                "SYSTEM",
                "审批已通过",
                "agent001",
                "APPROVAL_APPROVED",
                "审批通过通知",
                Map.of("approvalStatus", "APPROVED"));

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
        ArgumentCaptor<NotificationEventRequest> captor = ArgumentCaptor.forClass(NotificationEventRequest.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().payload())
                .containsEntry("messageRole", "SYSTEM")
                .containsEntry("userMessageDeliveryMode", "NOTIFICATION");
    }

    private WorkOrderView ticket() {
        Instant now = Instant.parse("2026-07-13T07:00:00Z");
        return new WorkOrderView(
                "wo_test",
                "trace_test",
                "s_test",
                "u1001",
                "human.takeover",
                "L2",
                "HUMAN_TAKEOVER",
                "PROCESSING",
                "NORMAL",
                "agent001",
                "用户请求人工客服",
                Map.of(),
                Map.of(),
                now.plusSeconds(1800),
                now,
                now,
                null);
    }
}
