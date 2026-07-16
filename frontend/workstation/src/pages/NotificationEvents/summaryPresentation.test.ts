import { describe, expect, it } from "vitest";
import type {
  NotificationDeliverySummary,
  NotificationOutboxSummary,
} from "../../types/notification";
import {
  getDeliveryMetrics,
  getOutboxMetrics,
  getSummaryState,
} from "./summaryPresentation";

describe("notification summary presentation", () => {
  it("marks disabled workers without treating them as failed", () => {
    expect(getSummaryState(false)).toEqual({ label: "未启用", color: "default" });
    expect(getSummaryState(true)).toEqual({ label: "运行中", color: "success" });
  });

  it("highlights due and exhausted outbox events", () => {
    const summary: NotificationOutboxSummary = {
      enabled: true,
      pending: 4,
      retryableFailed: 2,
      exhaustedFailed: 1,
      sent: 12,
      due: 3,
      leased: 1,
      oldestDueAt: "2026-07-16T00:00:00Z",
    };

    const metrics = getOutboxMetrics(summary);

    expect(metrics.find((metric) => metric.key === "due")?.tone).toBe("warning");
    expect(metrics.find((metric) => metric.key === "exhausted")?.tone).toBe("error");
    expect(metrics.find((metric) => metric.key === "completed")?.value).toBe(12);
  });

  it("uses delivered count for the Notification side", () => {
    const summary: NotificationDeliverySummary = {
      enabled: true,
      accepted: 2,
      retryableFailed: 0,
      exhaustedFailed: 0,
      delivered: 9,
      due: 0,
      leased: 1,
      oldestDueAt: null,
    };

    const metrics = getDeliveryMetrics(summary);

    expect(metrics.find((metric) => metric.key === "completed")).toMatchObject({
      label: "已投递",
      value: 9,
      tone: "success",
    });
    expect(metrics.find((metric) => metric.key === "due")?.tone).toBe("default");
  });
});
