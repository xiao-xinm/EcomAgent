package com.smartcs.agent.workbench.notification.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 仅在显式启用 Workbench 通知 outbox 时开启调度。 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "smartcs.notification.outbox.enabled", havingValue = "true")
public class NotificationOutboxSchedulingConfiguration {
}
