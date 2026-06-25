package com.smartcs.agent.notification.controller;

import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.common.util.TraceIds;
import com.smartcs.agent.notification.dto.NotificationEventDtos.NotificationEventRequest;
import com.smartcs.agent.notification.dto.NotificationEventDtos.NotificationEventResult;
import com.smartcs.agent.notification.service.NotificationEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives domain notification events from backend services.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationEventController {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationEventController.class);

    private final NotificationEventService notificationEventService;

    public NotificationEventController(NotificationEventService notificationEventService) {
        this.notificationEventService = notificationEventService;
    }

    @PostMapping("/events")
    public ApiResponse<NotificationEventResult> publish(@RequestBody NotificationEventRequest request) {
        String traceId = request.traceId() == null || request.traceId().isBlank()
                ? TraceIds.newTraceId()
                : request.traceId();
        LOGGER.info(
                "Notification event received traceId={} source={} eventType={} ticketId={} userId={}",
                traceId,
                request.sourceService(),
                request.eventType(),
                request.ticketId(),
                request.recipientUserId());
        NotificationEventResult result = notificationEventService.accept(request);
        return ApiResponse.success(result, traceId);
    }
}
