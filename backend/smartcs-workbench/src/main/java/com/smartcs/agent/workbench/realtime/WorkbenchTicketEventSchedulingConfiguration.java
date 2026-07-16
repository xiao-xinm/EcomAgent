package com.smartcs.agent.workbench.realtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 仅在显式启用坐席工单 SSE 时开启对应的增量扫描调度。 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "smartcs.realtime.ticket-events.enabled", havingValue = "true")
public class WorkbenchTicketEventSchedulingConfiguration {
}
