package com.smartcs.agent.workbench.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.common.dto.PageResult;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.ActionResult;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.InternalNoteRequest;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.OperatorActionRequest;
import com.smartcs.agent.workbench.ticket.WorkbenchDtos.TicketSummary;
import com.smartcs.agent.workbench.ticket.WorkbenchTicketService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkbenchTicketControllerTest {

    @Test
    void listTicketsWrapsPagedTicketResult() {
        WorkbenchTicketService ticketService = mock(WorkbenchTicketService.class);
        WorkbenchTicketController controller = new WorkbenchTicketController(ticketService);
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
    void claimDelegatesToTicketService() {
        WorkbenchTicketService ticketService = mock(WorkbenchTicketService.class);
        WorkbenchTicketController controller = new WorkbenchTicketController(ticketService);
        OperatorActionRequest request = new OperatorActionRequest("agent001", "领取工单", Map.of());
        ActionResult result = new ActionResult("wo_test", "ASSIGNED", "CLAIMED", null, "领取成功");
        when(ticketService.claim("wo_test", request)).thenReturn(result);

        ApiResponse<ActionResult> response = controller.claim("wo_test", request);

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.data()).isSameAs(result);
        assertThat(response.data().workOrderStatus()).isEqualTo("ASSIGNED");
        verify(ticketService).claim("wo_test", request);
    }

    @Test
    void addInternalNoteDelegatesToTicketService() {
        WorkbenchTicketService ticketService = mock(WorkbenchTicketService.class);
        WorkbenchTicketController controller = new WorkbenchTicketController(ticketService);
        InternalNoteRequest request = new InternalNoteRequest("agent001", "用户要求主管复核", Map.of("visibleToUser", false));
        ActionResult result = new ActionResult("wo_test", "PROCESSING", "CLAIMED", null, "内部备注已记录");
        when(ticketService.addInternalNote("wo_test", request)).thenReturn(result);

        ApiResponse<ActionResult> response = controller.addInternalNote("wo_test", request);

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.data()).isSameAs(result);
        assertThat(response.data().message()).isEqualTo("内部备注已记录");
        verify(ticketService).addInternalNote("wo_test", request);
    }
}
