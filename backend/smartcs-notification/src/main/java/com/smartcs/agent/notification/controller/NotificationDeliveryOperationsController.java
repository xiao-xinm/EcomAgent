package com.smartcs.agent.notification.controller;

import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.common.util.TraceIds;
import com.smartcs.agent.notification.retry.NotificationDeliveryOperationsService;
import com.smartcs.agent.notification.retry.NotificationDeliverySummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Notification 投递状态的只读运维接口。 */
@RestController
public class NotificationDeliveryOperationsController {

    private final NotificationDeliveryOperationsService operationsService;

    public NotificationDeliveryOperationsController(NotificationDeliveryOperationsService operationsService) {
        this.operationsService = operationsService;
    }

    @GetMapping("/api/notifications/events/delivery-summary")
    public ApiResponse<NotificationDeliverySummary> getSummary() {
        String traceId = TraceIds.newTraceId();
        return ApiResponse.success(operationsService.getSummary(), traceId);
    }
}
