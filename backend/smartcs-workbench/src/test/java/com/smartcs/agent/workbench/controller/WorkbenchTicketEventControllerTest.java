package com.smartcs.agent.workbench.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcs.agent.common.auth.AuthHeaders;
import com.smartcs.agent.common.auth.AuthRoles;
import com.smartcs.agent.common.auth.AuthenticatedPrincipal;
import com.smartcs.agent.workbench.auth.WorkbenchIdentityResolver;
import com.smartcs.agent.workbench.auth.WorkbenchReadAccessGuard;
import com.smartcs.agent.workbench.realtime.WorkbenchTicketEventStream;
import com.smartcs.agent.workbench.ticket.WorkbenchOperationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class WorkbenchTicketEventControllerTest {

    @Test
    void opensStreamForWorkbenchOperator() {
        WorkbenchTicketEventStream eventStream = mock(WorkbenchTicketEventStream.class);
        SseEmitter expected = new SseEmitter();
        when(eventStream.open(any(AuthenticatedPrincipal.class))).thenReturn(expected);
        WorkbenchTicketEventController controller = new WorkbenchTicketEventController(
                new WorkbenchReadAccessGuard(new WorkbenchIdentityResolver()),
                eventStream);
        HttpHeaders headers = new HttpHeaders();
        headers.add(AuthHeaders.DEV_OPERATOR_ID, "agent_stream");
        headers.add(AuthHeaders.ROLES, AuthRoles.AGENT);

        assertThat(controller.stream(headers)).isSameAs(expected);
        verify(eventStream).open(any(AuthenticatedPrincipal.class));
    }

    @Test
    void rejectsCustomerPrincipal() {
        WorkbenchTicketEventController controller = new WorkbenchTicketEventController(
                new WorkbenchReadAccessGuard(new WorkbenchIdentityResolver()),
                mock(WorkbenchTicketEventStream.class));
        HttpHeaders headers = new HttpHeaders();
        headers.add(AuthHeaders.PRINCIPAL_ID, "u1001");
        headers.add(AuthHeaders.PRINCIPAL_TYPE, "CUSTOMER");
        headers.add(AuthHeaders.ROLES, AuthRoles.CUSTOMER);

        assertThatThrownBy(() -> controller.stream(headers))
                .isInstanceOfSatisfying(WorkbenchOperationException.class, exception ->
                        assertThat(exception.errorCode().code()).isEqualTo("1003"));
    }
}
