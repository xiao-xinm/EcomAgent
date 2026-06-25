package com.smartcs.agent.notification.service;

import com.smartcs.agent.notification.dto.NotificationEventDtos.NotificationEventRequest;
import com.smartcs.agent.notification.dto.NotificationEventDtos.NotificationEventResult;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 最小通知事件服务。
 *
 * 当前阶段不接 RocketMQ、不落库，只记录结构化日志并返回 ACCEPTED。
 * 后续需要站内信、短信、坐席提醒时，可以在这里替换为持久化或消息队列投递。
 */
@Service
public class NotificationEventService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationEventService.class);

    public NotificationEventResult accept(NotificationEventRequest request) {
        String eventId = request.eventId() == null || request.eventId().isBlank()
                ? "ntf_" + UUID.randomUUID()
                : request.eventId();
        String channel = request.channel() == null || request.channel().isBlank()
                ? "USER_SESSION"
                : request.channel();
        Instant acceptedAt = Instant.now();
        LOGGER.info(
                "Notification event accepted eventId={} traceId={} eventType={} channel={} ticketId={} sessionId={} userId={} operatorId={} title={} contentLength={}",
                eventId,
                request.traceId(),
                request.eventType(),
                channel,
                request.ticketId(),
                request.sessionId(),
                request.recipientUserId(),
                request.operatorId(),
                request.title(),
                request.content() == null ? 0 : request.content().length());
        return new NotificationEventResult(eventId, "ACCEPTED", channel, acceptedAt);
    }
}
