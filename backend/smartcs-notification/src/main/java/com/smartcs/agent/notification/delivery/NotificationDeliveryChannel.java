package com.smartcs.agent.notification.delivery;

/** 可插拔通知投递通道。实现必须使用 eventId 保证重复调用幂等。 */
public interface NotificationDeliveryChannel {

    String channel();

    void deliver(NotificationDeliveryCommand command);
}
