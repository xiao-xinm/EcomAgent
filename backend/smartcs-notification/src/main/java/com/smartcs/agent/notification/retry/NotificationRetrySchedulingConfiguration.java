package com.smartcs.agent.notification.retry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 仅在显式开启通知重试时启用 Spring 定时调度。 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "smartcs.notification.retry.enabled", havingValue = "true")
public class NotificationRetrySchedulingConfiguration {
}
