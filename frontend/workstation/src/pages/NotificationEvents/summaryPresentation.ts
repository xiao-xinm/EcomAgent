import type {
  NotificationDeliverySummary,
  NotificationOutboxSummary,
} from "../../types/notification";

export type SummaryMetricTone = "default" | "processing" | "warning" | "error" | "success";

export interface SummaryMetric {
  key: string;
  label: string;
  value: number;
  tone: SummaryMetricTone;
}

export interface SummaryState {
  label: string;
  color: "default" | "success";
}

export interface CutoverState {
  label: string;
  description: string;
  color: "default" | "processing" | "warning" | "error" | "success";
}

export function getSummaryState(enabled: boolean): SummaryState {
  return enabled
    ? { label: "运行中", color: "success" }
    : { label: "未启用", color: "default" };
}

export function getCutoverState(
  outbox?: NotificationOutboxSummary,
  delivery?: NotificationDeliverySummary,
): CutoverState | null {
  if (!outbox || !delivery) {
    return null;
  }

  const asyncConfigurationReady = outbox.enabled
    && outbox.notificationEnabled
    && delivery.enabled
    && delivery.userSessionChannelEnabled;
  const exhausted = outbox.exhaustedFailed + delivery.exhaustedFailed;
  const outstanding = outbox.pending
    + outbox.retryableFailed
    + outbox.leased
    + delivery.accepted
    + delivery.retryableFailed
    + delivery.leased;

  if (outbox.userMessageDeliveryMode === "NOTIFICATION" && !asyncConfigurationReady) {
    return {
      label: "异步配置异常",
      description: "消息直写已关闭，但异步投递链路未完整开启",
      color: "error",
    };
  }
  if (exhausted > 0) {
    return {
      label: "存在重试耗尽",
      description: "先处理失败事件，再继续异步切换或运行",
      color: "error",
    };
  }
  if (asyncConfigurationReady && (outstanding > 0 || outbox.due > 0 || delivery.due > 0)) {
    return {
      label: outbox.userMessageDeliveryMode === "DIRECT" ? "灰度观察有积压" : "异步交付有积压",
      description: "等待待处理、重试和租约任务清空",
      color: "warning",
    };
  }
  if (outbox.userMessageDeliveryMode === "NOTIFICATION") {
    return {
      label: "异步交付中",
      description: "Workbench 直写已关闭，用户消息由 Notification 投递",
      color: "success",
    };
  }
  if (asyncConfigurationReady) {
    return {
      label: "可切换异步",
      description: "异步链路已开启且当前无积压",
      color: "processing",
    };
  }
  return {
    label: "同步直写中",
    description: "用户消息仍由 Workbench 事务内写入",
    color: "default",
  };
}

export function getOutboxMetrics(summary: NotificationOutboxSummary): SummaryMetric[] {
  return [
    { key: "pending", label: "待处理", value: summary.pending, tone: "processing" },
    { key: "due", label: "当前到期", value: summary.due, tone: summary.due > 0 ? "warning" : "default" },
    { key: "retryable", label: "失败待重试", value: summary.retryableFailed, tone: summary.retryableFailed > 0 ? "warning" : "default" },
    { key: "exhausted", label: "重试耗尽", value: summary.exhaustedFailed, tone: summary.exhaustedFailed > 0 ? "error" : "default" },
    { key: "leased", label: "租约中", value: summary.leased, tone: "processing" },
    { key: "completed", label: "已发送", value: summary.sent, tone: "success" },
  ];
}

export function getDeliveryMetrics(summary: NotificationDeliverySummary): SummaryMetric[] {
  return [
    { key: "accepted", label: "已接收", value: summary.accepted, tone: "processing" },
    { key: "due", label: "当前到期", value: summary.due, tone: summary.due > 0 ? "warning" : "default" },
    { key: "retryable", label: "失败待重试", value: summary.retryableFailed, tone: summary.retryableFailed > 0 ? "warning" : "default" },
    { key: "exhausted", label: "重试耗尽", value: summary.exhaustedFailed, tone: summary.exhaustedFailed > 0 ? "error" : "default" },
    { key: "leased", label: "租约中", value: summary.leased, tone: "processing" },
    { key: "completed", label: "已投递", value: summary.delivered, tone: "success" },
  ];
}
