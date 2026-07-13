package com.smartcs.agent.workbench.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.workbench.auth.WorkbenchIdentityResolver;
import com.smartcs.agent.workbench.auth.WorkbenchReadAccessGuard;
import com.smartcs.agent.workbench.notification.outbox.NotificationOutboxOperationsService;
import com.smartcs.agent.workbench.notification.outbox.NotificationOutboxSummary;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class NotificationOutboxControllerTest {

    @Test
    void getSummaryRequiresOperatorAndWrapsServiceResult() {
        NotificationOutboxOperationsService service = mock(NotificationOutboxOperationsService.class);
        NotificationOutboxSummary expected = new NotificationOutboxSummary(
                true, 3, 2, 1, 12, 4, 1, Instant.parse("2026-07-13T08:00:00Z"));
        when(service.getSummary()).thenReturn(expected);
        NotificationOutboxController controller = new NotificationOutboxController(
                service,
                new WorkbenchReadAccessGuard(new WorkbenchIdentityResolver()));

        ApiResponse<NotificationOutboxSummary> response = controller.getSummary(HttpHeaders.EMPTY);

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.traceId()).isNotBlank();
        assertThat(response.data()).isSameAs(expected);
        verify(service).getSummary();
    }
}
