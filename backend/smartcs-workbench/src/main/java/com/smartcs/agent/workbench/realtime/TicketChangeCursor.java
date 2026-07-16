package com.smartcs.agent.workbench.realtime;

import java.time.Instant;

/** 以时间和稳定主键组成游标，避免同一毫秒内多条变化被跳过。 */
record TicketChangeCursor(Instant changedAt, String tieBreaker) {

    TicketChangeCursor {
        if (changedAt == null || tieBreaker == null) {
            throw new IllegalArgumentException("工单变化游标不能为空");
        }
    }
}
