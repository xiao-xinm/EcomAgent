package com.smartcs.agent.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.notification.dto.NotificationEventDtos.NotificationEventRequest;
import com.smartcs.agent.notification.dto.NotificationEventDtos.NotificationEventResult;
import com.smartcs.agent.notification.service.NotificationEventService;
import java.time.Instant;
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
}
