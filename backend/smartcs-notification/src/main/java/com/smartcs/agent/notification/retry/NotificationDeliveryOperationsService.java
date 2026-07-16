package com.smartcs.agent.notification.retry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 控制投递摘要是否读取租约字段，兼容默认关闭且未执行 13 号迁移的环境。 */
@Service
public class NotificationDeliveryOperationsService {

    private final NotificationRetryRepository repository;
    private final boolean retryEnabled;
    private final int maxAttempts;

    public NotificationDeliveryOperationsService(
            NotificationRetryRepository repository,
            @Value("${smartcs.notification.retry.enabled:false}") boolean retryEnabled,
            @Value("${smartcs.notification.retry.max-attempts:5}") int maxAttempts) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts 必须大于 0");
        }
        this.repository = repository;
        this.retryEnabled = retryEnabled;
        this.maxAttempts = maxAttempts;
    }

    public NotificationDeliverySummary getSummary() {
        if (!retryEnabled) {
            return NotificationDeliverySummary.disabled();
        }
        return repository.summarize(maxAttempts);
    }
}
