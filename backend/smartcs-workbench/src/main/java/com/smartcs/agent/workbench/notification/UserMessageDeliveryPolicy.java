package com.smartcs.agent.workbench.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 控制用户可见消息由 Workbench 直写，还是交给 Notification 异步写入。 */
@Component
public class UserMessageDeliveryPolicy {

    static final String DIRECT = "DIRECT";
    static final String NOTIFICATION = "NOTIFICATION";

    private final boolean directWriteEnabled;

    public UserMessageDeliveryPolicy(
            @Value("${smartcs.notification.user-message-direct-write-enabled:true}") boolean directWriteEnabled,
            @Value("${smartcs.notification.outbox.enabled:false}") boolean outboxEnabled) {
        if (!directWriteEnabled && !outboxEnabled) {
            throw new IllegalArgumentException("关闭 Workbench 用户消息直写时必须同时启用通知 outbox");
        }
        this.directWriteEnabled = directWriteEnabled;
    }

    public boolean directWriteEnabled() {
        return directWriteEnabled;
    }

    public String deliveryMode() {
        return directWriteEnabled ? DIRECT : NOTIFICATION;
    }
}
