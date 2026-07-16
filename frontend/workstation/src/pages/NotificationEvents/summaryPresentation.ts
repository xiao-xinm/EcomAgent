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

export function getSummaryState(enabled: boolean): SummaryState {
  return enabled
    ? { label: "运行中", color: "success" }
    : { label: "未启用", color: "default" };
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
