package com.smartcs.agent.workbench.notification.outbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 控制 outbox 运维查询是否访问数据库，兼容默认关闭且未建表的开发环境。 */
@Service
public class NotificationOutboxOperationsService {

    private final NotificationOutboxRepository repository;
    private final boolean outboxEnabled;
    private final int maxAttempts;

    public NotificationOutboxOperationsService(
            NotificationOutboxRepository repository,
            @Value("${smartcs.notification.outbox.enabled:false}") boolean outboxEnabled,
            @Value("${smartcs.notification.outbox.max-attempts:5}") int maxAttempts) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts 必须大于 0");
        }
        this.repository = repository;
        this.outboxEnabled = outboxEnabled;
        this.maxAttempts = maxAttempts;
    }

    public NotificationOutboxSummary getSummary() {
        if (!outboxEnabled) {
            return NotificationOutboxSummary.disabled();
        }
        return repository.summarize(maxAttempts);
    }
}
