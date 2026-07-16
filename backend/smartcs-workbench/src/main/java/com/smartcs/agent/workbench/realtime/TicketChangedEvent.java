package com.smartcs.agent.workbench.realtime;

import java.time.Instant;

/** 坐席端 SSE 使用的轻量工单变化通知，完整数据仍通过工单查询接口读取。 */
public record TicketChangedEvent(
        String eventId,
        String ticketId,
        String status,
        String assignedAgent,
        Instant changedAt) {
}
