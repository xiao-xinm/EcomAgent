package com.smartcs.agent.workbench.realtime;

import java.util.List;

/** 单个事实来源的一批增量工单变化及其下一游标。 */
record TicketChangeBatch(List<TicketChangedEvent> events, TicketChangeCursor nextCursor) {

    TicketChangeBatch {
        events = events == null ? List.of() : List.copyOf(events);
        if (nextCursor == null) {
            throw new IllegalArgumentException("下一游标不能为空");
        }
    }
}
