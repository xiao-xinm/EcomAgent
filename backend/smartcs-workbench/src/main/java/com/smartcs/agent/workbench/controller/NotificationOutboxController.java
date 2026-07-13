package com.smartcs.agent.workbench.controller;

import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.common.util.TraceIds;
import com.smartcs.agent.workbench.auth.WorkbenchReadAccessGuard;
import com.smartcs.agent.workbench.notification.outbox.NotificationOutboxOperationsService;
import com.smartcs.agent.workbench.notification.outbox.NotificationOutboxSummary;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Workbench 通知 outbox 的只读运维接口。 */
@RestController
public class NotificationOutboxController {

    private static final String APPLICATION_JSON_UTF8 = "application/json;charset=UTF-8";

    private final NotificationOutboxOperationsService operationsService;
    private final WorkbenchReadAccessGuard accessGuard;

    public NotificationOutboxController(
            NotificationOutboxOperationsService operationsService,
            WorkbenchReadAccessGuard accessGuard) {
        this.operationsService = operationsService;
        this.accessGuard = accessGuard;
    }

    @GetMapping(
            value = "/api/workbench/notifications/outbox/summary",
            produces = APPLICATION_JSON_UTF8)
    public ApiResponse<NotificationOutboxSummary> getSummary(@RequestHeader HttpHeaders headers) {
        String traceId = TraceIds.newTraceId();
        accessGuard.requireOperator(headers);
        return ApiResponse.success(operationsService.getSummary(), traceId);
    }
}
