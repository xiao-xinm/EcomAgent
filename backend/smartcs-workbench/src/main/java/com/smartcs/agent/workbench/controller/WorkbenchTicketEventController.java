package com.smartcs.agent.workbench.controller;

import com.smartcs.agent.common.auth.AuthenticatedPrincipal;
import com.smartcs.agent.workbench.auth.WorkbenchReadAccessGuard;
import com.smartcs.agent.workbench.realtime.WorkbenchTicketEventStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 坐席工作台工单变化的单向 SSE 提醒入口。 */
@RestController
@ConditionalOnProperty(name = "smartcs.realtime.ticket-events.enabled", havingValue = "true")
public class WorkbenchTicketEventController {

    private final WorkbenchReadAccessGuard accessGuard;
    private final WorkbenchTicketEventStream eventStream;

    public WorkbenchTicketEventController(
            WorkbenchReadAccessGuard accessGuard,
            WorkbenchTicketEventStream eventStream) {
        this.accessGuard = accessGuard;
        this.eventStream = eventStream;
    }

    @GetMapping(value = "/api/workbench/tickets/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestHeader HttpHeaders headers) {
        AuthenticatedPrincipal principal = accessGuard.requireOperator(headers);
        return eventStream.open(principal);
    }
}
