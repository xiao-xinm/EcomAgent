package com.smartcs.agent.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.notification.retry.NotificationDeliveryOperationsService;
import com.smartcs.agent.notification.retry.NotificationDeliverySummary;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class NotificationDeliveryOperationsControllerTest {

    @Test
    void getSummaryWrapsServiceResult() {
        NotificationDeliveryOperationsService service = mock(NotificationDeliveryOperationsService.class);
        NotificationDeliverySummary expected = new NotificationDeliverySummary(
                true, 3, 2, 1, 12, 4, 1, Instant.parse("2026-07-13T08:00:00Z"));
        when(service.getSummary()).thenReturn(expected);
        NotificationDeliveryOperationsController controller =
                new NotificationDeliveryOperationsController(service);

        ApiResponse<NotificationDeliverySummary> response = controller.getSummary();

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.traceId()).isNotBlank();
        assertThat(response.data()).isSameAs(expected);
        verify(service).getSummary();
    }
}
