import { workstationRuntimeConfig } from "../config/runtime";
import type { TicketChangedEvent, WorkOrderStatus } from "../types/workbench";

const WORK_ORDER_STATUSES: WorkOrderStatus[] = [
  "PENDING",
  "ASSIGNED",
  "PROCESSING",
  "APPROVED",
  "REJECTED",
  "RESOLVED",
  "CLOSED",
  "ESCALATED",
];

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

export function parseTicketChangedEvent(data: string): TicketChangedEvent | null {
  try {
    const value: unknown = JSON.parse(data);
    if (!isRecord(value)) return null;
    if (
      typeof value.eventId !== "string"
      || typeof value.ticketId !== "string"
      || typeof value.status !== "string"
      || !WORK_ORDER_STATUSES.includes(value.status as WorkOrderStatus)
      || typeof value.changedAt !== "string"
    ) {
      return null;
    }
    return {
      eventId: value.eventId,
      ticketId: value.ticketId,
      status: value.status as WorkOrderStatus,
      assignedAgent: typeof value.assignedAgent === "string" ? value.assignedAgent : null,
      changedAt: value.changedAt,
    };
  } catch {
    return null;
  }
}

export function createTicketEventSource(): EventSource {
  const baseUrl = workstationRuntimeConfig.apiBaseUrl.replace(/\/$/, "");
  const url = new URL(
    `${baseUrl}/api/workbench/tickets/events`,
    window.location.origin,
  );
  return new EventSource(url.toString());
}
