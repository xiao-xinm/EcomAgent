import type { PageResult } from "./workbench";

// Types derived from docs/notification-api.md.
// The workstation currently only reads notification events and does not change delivery state.

export type NotificationEventStatus = "ACCEPTED" | "DELIVERED" | "FAILED";

export interface NotificationEventView {
  eventId: string;
  traceId?: string | null;
  sourceService?: string | null;
  eventType: string;
  recipientUserId?: string | null;
  sessionId?: string | null;
  ticketId?: string | null;
  operatorId?: string | null;
  channel: string;
  title?: string | null;
  content?: string | null;
  payload: Record<string, unknown>;
  status: NotificationEventStatus;
  retryCount: number;
  lastError?: string | null;
  nextRetryAt?: string | null;
  deliveredAt?: string | null;
  occurredAt: string;
  acceptedAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface NotificationEventQueryParams {
  eventType?: string;
  ticketId?: string;
  recipientUserId?: string;
  status?: NotificationEventStatus;
  pageNo?: number;
  pageSize?: number;
}

export type NotificationEventPageResult = PageResult<NotificationEventView>;

export interface NotificationOutboxSummary {
  enabled: boolean;
  pending: number;
  retryableFailed: number;
  exhaustedFailed: number;
  sent: number;
  due: number;
  leased: number;
  oldestDueAt?: string | null;
}

export interface NotificationDeliverySummary {
  enabled: boolean;
  accepted: number;
  retryableFailed: number;
  exhaustedFailed: number;
  delivered: number;
  due: number;
  leased: number;
  oldestDueAt?: string | null;
}
