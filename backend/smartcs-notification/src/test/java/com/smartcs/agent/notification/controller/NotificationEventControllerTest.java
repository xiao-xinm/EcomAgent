package com.smartcs.agent.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.common.dto.PageResult;
import com.smartcs.agent.notification.dto.NotificationEventDtos.NotificationDeliveryResult;
import com.smartcs.agent.notification.dto.NotificationEventDtos.NotificationDeliveryResultRequest;
import com.smartcs.agent.notification.dto.NotificationEventDtos.NotificationEventRequest;
import com.smartcs.agent.notification.dto.NotificationEventDtos.NotificationEventResult;
import com.smartcs.agent.notification.dto.NotificationEventDtos.NotificationEventView;
import com.smartcs.agent.notification.service.NotificationEventService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationEventControllerTest {

    @Test
    void publishKeepsProvidedTraceIdAndReturnsAcceptedResult() {
        NotificationEventService notificationEventService = mock(NotificationEventService.class);
        NotificationEventController controller = new NotificationEventController(notificationEventService);
        NotificationEventRequest request = new NotificationEventRequest(
                "evt_test",
                "trace_notice",
                "smartcs-workbench",
                "WORKBENCH_APPROVED",
                "u1001",
                "s_test",
                "wo_test",
                "agent001",
                "h5",
                "审批结果",
                "退款申请已审核。",
                Map.of("ticketId", "wo_test"),
                Instant.now());
        NotificationEventResult result = new NotificationEventResult(
                "evt_test",
                "ACCEPTED",
                "h5",
                Instant.now());
        when(notificationEventService.accept(request)).thenReturn(result);

        ApiResponse<NotificationEventResult> response = controller.publish(request);

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.traceId()).isEqualTo("trace_notice");
        assertThat(response.data()).isSameAs(result);
        assertThat(response.data().status()).isEqualTo("ACCEPTED");
        verify(notificationEventService).accept(request);
    }

    @Test
    void listReturnsPagedNotificationEvents() {
        NotificationEventService notificationEventService = mock(NotificationEventService.class);
        NotificationEventController controller = new NotificationEventController(notificationEventService);
        Instant now = Instant.now();
        PageResult<NotificationEventView> page = new PageResult<>(
                List.of(new NotificationEventView(
                        "evt_test",
                        "trace_notice",
                        "smartcs-workbench",
                        "APPROVAL_APPROVED",
                        "u1001",
                        "s_test",
                        "wo_test",
                        "agent001",
                        "USER_SESSION",
                        "审批通过通知",
                        "审批已通过",
                        Map.of("ticketId", "wo_test"),
                        "ACCEPTED",
                        0,
                        null,
                        null,
                        null,
                        now,
                        now,
                        now,
                        now)),
                1,
                1,
                20);
        when(notificationEventService.list("APPROVAL_APPROVED", "wo_test", "u1001", "ACCEPTED", 1, 20))
                .thenReturn(page);

        ApiResponse<PageResult<NotificationEventView>> response = controller.list(
                "APPROVAL_APPROVED",
                "wo_test",
                "u1001",
                "ACCEPTED",
                1,
                20);

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.data()).isSameAs(page);
        assertThat(response.data().records()).hasSize(1);
        verify(notificationEventService).list("APPROVAL_APPROVED", "wo_test", "u1001", "ACCEPTED", 1, 20);
    }

    @Test
    void recordDeliveryResultReturnsUpdatedStatus() {
        NotificationEventService notificationEventService = mock(NotificationEventService.class);
        NotificationEventController controller = new NotificationEventController(notificationEventService);
        Instant nextRetryAt = Instant.now().plusSeconds(60);
        NotificationDeliveryResultRequest request = new NotificationDeliveryResultRequest(
                "FAILED",
                "站内信通道暂不可用",
                nextRetryAt);
        NotificationDeliveryResult result = new NotificationDeliveryResult(
                "evt_test",
                "FAILED",
                1,
                "站内信通道暂不可用",
                nextRetryAt,
                null,
                Instant.now());
        when(notificationEventService.recordDeliveryResult("evt_test", request)).thenReturn(result);

        ApiResponse<NotificationDeliveryResult> response = controller.recordDeliveryResult("evt_test", request);

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.data()).isSameAs(result);
        assertThat(response.data().retryCount()).isEqualTo(1);
        verify(notificationEventService).recordDeliveryResult("evt_test", request);
    }
}
