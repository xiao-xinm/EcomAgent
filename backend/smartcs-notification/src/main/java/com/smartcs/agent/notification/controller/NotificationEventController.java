package com.smartcs.agent.notification.controller;

import com.smartcs.agent.common.dto.ApiResponse;
import com.smartcs.agent.common.dto.PageResult;
import com.smartcs.agent.common.util.TraceIds;
import com.smartcs.agent.notification.dto.NotificationEventDtos.NotificationEventRequest;
import com.smartcs.agent.notification.dto.NotificationEventDtos.NotificationEventResult;
import com.smartcs.agent.notification.dto.NotificationEventDtos.NotificationEventView;
import com.smartcs.agent.notification.service.NotificationEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/events")
    public ApiResponse<PageResult<NotificationEventView>> list(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String ticketId,
            @RequestParam(required = false) String recipientUserId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        String traceId = TraceIds.newTraceId();
        LOGGER.info(
                "Notification events list requested traceId={} eventType={} ticketId={} recipientUserId={} status={} pageNo={} pageSize={}",
                traceId,
                eventType,
                ticketId,
                recipientUserId,
                status,
                pageNo,
                pageSize);
        PageResult<NotificationEventView> result = notificationEventService.list(
                eventType,
                ticketId,
                recipientUserId,
                status,
                pageNo,
                pageSize);
        LOGGER.info("Notification events list completed traceId={} total={}", traceId, result.total());
        return ApiResponse.success(result, traceId);
    }
}
