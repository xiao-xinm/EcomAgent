package com.smartcs.agent.workbench.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartcs.agent.common.auth.AuthHeaders;
import com.smartcs.agent.common.auth.AuthPrincipalException;
import com.smartcs.agent.common.auth.AuthRoles;
import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.common.dto.PageResult;
import com.smartcs.agent.workbench.auth.WorkbenchIdentityResolver;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.ActionResult;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.CurrentOperatorView;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.InternalNoteRequest;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.OperatorActionRequest;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.TakeoverMessageRequest;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.TicketStatsView;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.TicketSummary;
import com.smartcs.agent.workbench.ticket.WorkbenchOperationException;
import com.smartcs.agent.workbench.ticket.WorkbenchTicketService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

class WorkbenchTicketControllerTest {

    @Test
    void listTicketsWrapsPagedTicketResult() {
        WorkbenchTicketService ticketService = mock(WorkbenchTicketService.class);
        WorkbenchTicketController controller = newController(ticketService);
        TicketSummary ticket = new TicketSummary(
                "wo_test",
                "trace_workbench",
                "s_test",
                "u1001",
                "refund.apply",
                "L3",
                "HUMAN_REVIEW",
                "PENDING",
                "HIGH",
                null,
                "用户申请退款",
                null,
                Instant.now(),
                Instant.now(),
                "ap_test",
                "REFUND",
                "PENDING",
                null,
                null);
        PageResult<TicketSummary> page = new PageResult<>(List.of(ticket), 1, 1, 20);
        when(ticketService.listTickets(
                "PENDING",
                "HUMAN_REVIEW",
                "L3",
                "refund.apply",
                "HIGH",
                "agent001",
                "refund",
                "2026-07-01 00:00:00",
                "2026-07-04 23:59:59",
                1,
                20)).thenReturn(page);

        ApiResponse<PageResult<TicketSummary>> response =
                controller.listTickets(
                        HttpHeaders.EMPTY,
                        "PENDING",
                        "HUMAN_REVIEW",
                        "L3",
                        "refund.apply",
                        "HIGH",
                        "agent001",
                        "refund",
                        "2026-07-01 00:00:00",
                        "2026-07-04 23:59:59",
                        1,
                        20);

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.traceId()).isNotBlank();
        assertThat(response.data().records()).containsExactly(ticket);
        assertThat(response.data().total()).isEqualTo(1);
        verify(ticketService).listTickets(
                "PENDING",
                "HUMAN_REVIEW",
                "L3",
                "refund.apply",
                "HIGH",
                "agent001",
                "refund",
                "2026-07-01 00:00:00",
                "2026-07-04 23:59:59",
                1,
                20);
    }

    @Test
    void getTicketStatsWrapsStatsResult() {
        WorkbenchTicketService ticketService = mock(WorkbenchTicketService.class);
        WorkbenchTicketController controller = newController(ticketService);
        TicketStatsView stats = new TicketStatsView(12, 4, 3, 5, 1);
        when(ticketService.getTicketStats()).thenReturn(stats);

        ApiResponse<TicketStatsView> response = controller.getTicketStats(HttpHeaders.EMPTY);

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.traceId()).isNotBlank();
        assertThat(response.data()).isSameAs(stats);
        assertThat(response.data().overdueRisk()).isEqualTo(1);
        verify(ticketService).getTicketStats();
    }

    @Test
    void claimDelegatesToTicketService() {
        WorkbenchTicketService ticketService = mock(WorkbenchTicketService.class);
        WorkbenchTicketController controller = newController(ticketService);
        OperatorActionRequest request = new OperatorActionRequest("agent001", "领取工单", Map.of());
        ActionResult result = new ActionResult("wo_test", "ASSIGNED", "CLAIMED", null, "领取成功");
        when(ticketService.claim("wo_test", request)).thenReturn(result);

        ApiResponse<ActionResult> response = controller.claim("wo_test", HttpHeaders.EMPTY, request);

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.data()).isSameAs(result);
        assertThat(response.data().workOrderStatus()).isEqualTo("ASSIGNED");
        verify(ticketService).claim("wo_test", request);
    }

    @Test
    void addInternalNoteDelegatesToTicketService() {
        WorkbenchTicketService ticketService = mock(WorkbenchTicketService.class);
        WorkbenchTicketController controller = newController(ticketService);
        InternalNoteRequest request = new InternalNoteRequest("agent001", "用户要求主管复核", Map.of("visibleToUser", false));
        ActionResult result = new ActionResult("wo_test", "PROCESSING", "CLAIMED", null, "内部备注已记录");
        when(ticketService.addInternalNote("wo_test", request)).thenReturn(result);

        ApiResponse<ActionResult> response = controller.addInternalNote("wo_test", HttpHeaders.EMPTY, request);

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.data()).isSameAs(result);
        assertThat(response.data().message()).isEqualTo("内部备注已记录");
        verify(ticketService).addInternalNote("wo_test", request);
    }

    @Test
    void sendTakeoverMessageDelegatesToTicketService() {
        WorkbenchTicketService ticketService = mock(WorkbenchTicketService.class);
        WorkbenchTicketController controller = newController(ticketService);
        TakeoverMessageRequest request = new TakeoverMessageRequest("agent001", "我正在帮你核实", Map.of());
        ActionResult result = new ActionResult("wo_test", "PROCESSING", null, "IN_PROGRESS", "人工消息已发送");
        when(ticketService.sendTakeoverMessage("wo_test", request)).thenReturn(result);

        ApiResponse<ActionResult> response = controller.sendTakeoverMessage("wo_test", HttpHeaders.EMPTY, request);

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.data()).isSameAs(result);
        assertThat(response.data().takeoverStatus()).isEqualTo("IN_PROGRESS");
        verify(ticketService).sendTakeoverMessage("wo_test", request);
    }

    @Test
    void claimUsesDevHeaderOperatorWhenPresent() {
        WorkbenchTicketService ticketService = mock(WorkbenchTicketService.class);
        WorkbenchTicketController controller = newController(ticketService);
        HttpHeaders headers = new HttpHeaders();
        headers.add(AuthHeaders.DEV_OPERATOR_ID, "agent_header");
        OperatorActionRequest request = new OperatorActionRequest("agent_body", "领取工单", Map.of());
        OperatorActionRequest rewritten = new OperatorActionRequest("agent_header", "领取工单", Map.of());
        ActionResult result = new ActionResult("wo_test", "ASSIGNED", "CLAIMED", null, "领取成功");
        when(ticketService.claim("wo_test", rewritten)).thenReturn(result);

        ApiResponse<ActionResult> response = controller.claim("wo_test", headers, request);

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.data()).isSameAs(result);
        verify(ticketService).claim("wo_test", rewritten);
    }

    @Test
    void claimRejectsDevOperatorWithoutWorkbenchRole() {
        WorkbenchTicketService ticketService = mock(WorkbenchTicketService.class);
        WorkbenchTicketController controller = newController(ticketService);
        HttpHeaders headers = new HttpHeaders();
        headers.add(AuthHeaders.DEV_OPERATOR_ID, "agent_header");
        headers.add(AuthHeaders.ROLES, AuthRoles.CUSTOMER);
        OperatorActionRequest request = new OperatorActionRequest("agent_body", "棰嗗彇宸ュ崟", Map.of());

        assertThatThrownBy(() -> controller.claim("wo_test", headers, request))
                .isInstanceOfSatisfying(WorkbenchOperationException.class, exception ->
                        assertThat(exception.errorCode().code()).isEqualTo("1003"));
        verifyNoInteractions(ticketService);
    }

    @Test
    void standardCustomerPrincipalCannotFallbackToLegacyOperatorBody() {
        WorkbenchTicketService ticketService = mock(WorkbenchTicketService.class);
        WorkbenchTicketController controller = newController(ticketService);
        HttpHeaders headers = new HttpHeaders();
        headers.add(AuthHeaders.PRINCIPAL_ID, "u1001");
        headers.add(AuthHeaders.PRINCIPAL_TYPE, "CUSTOMER");
        headers.add(AuthHeaders.ROLES, AuthRoles.CUSTOMER);
        OperatorActionRequest request = new OperatorActionRequest("agent_body", "棰嗗彇宸ュ崟", Map.of());

        assertThatThrownBy(() -> controller.claim("wo_test", headers, request))
                .isInstanceOfSatisfying(WorkbenchOperationException.class, exception ->
                        assertThat(exception.errorCode().code()).isEqualTo("1003"));
        verifyNoInteractions(ticketService);
    }

    @Test
    void getCurrentOperatorFallsBackToDevelopmentOperator() {
        WorkbenchTicketService ticketService = mock(WorkbenchTicketService.class);
        WorkbenchTicketController controller = newController(ticketService);

        ApiResponse<CurrentOperatorView> response = controller.getCurrentOperator(HttpHeaders.EMPTY);

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.data().operatorId()).isEqualTo("agent_001");
        assertThat(response.data().principalType()).isEqualTo("AGENT");
        assertThat(response.data().roles()).containsExactly("AGENT");
        assertThat(response.data().authSource()).isEqualTo("DEV_FALLBACK");
    }

    @Test
    void strictAuthRequiresTrustedOperatorForListTickets() {
        WorkbenchTicketService ticketService = mock(WorkbenchTicketService.class);
        WorkbenchIdentityResolver resolver = new WorkbenchIdentityResolver();
        ReflectionTestUtils.setField(resolver, "strictAuthEnabled", true);
        WorkbenchTicketController controller = new WorkbenchTicketController(ticketService, resolver);

        assertThatThrownBy(() -> controller.listTickets(
                HttpHeaders.EMPTY,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                1,
                20))
                .isInstanceOfSatisfying(AuthPrincipalException.class, exception ->
                        assertThat(exception.errorCode().code()).isEqualTo("1002"));
        verifyNoInteractions(ticketService);
    }

    private WorkbenchTicketController newController(WorkbenchTicketService ticketService) {
        return new WorkbenchTicketController(ticketService, new WorkbenchIdentityResolver());
    }
}
