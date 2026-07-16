import { describe, expect, it } from "vitest";
import { isTicketSseCompatible } from "../config/runtime";
import { parseTicketChangedEvent } from "./ticketEvents";

describe("ticket event helpers", () => {
  it("parses a valid ticket.changed payload", () => {
    expect(parseTicketChangedEvent(JSON.stringify({
      eventId: "evt_1",
      ticketId: "wo_1",
      status: "PROCESSING",
      assignedAgent: "agent_001",
      changedAt: "2026-07-16T08:00:00Z",
    }))).toEqual({
      eventId: "evt_1",
      ticketId: "wo_1",
      status: "PROCESSING",
      assignedAgent: "agent_001",
      changedAt: "2026-07-16T08:00:00Z",
    });
  });

  it("rejects malformed or unknown-status payloads", () => {
    expect(parseTicketChangedEvent("not-json")).toBeNull();
    expect(parseTicketChangedEvent(JSON.stringify({
      eventId: "evt_1",
      ticketId: "wo_1",
      status: "UNKNOWN",
      changedAt: "2026-07-16T08:00:00Z",
    }))).toBeNull();
  });

  it("keeps SSE disabled when strict bearer authentication is required", () => {
    expect(isTicketSseCompatible(
      { ticketSseEnabled: true, authRequired: true },
      true,
    )).toBe(false);
    expect(isTicketSseCompatible(
      { ticketSseEnabled: true, authRequired: false },
      true,
    )).toBe(true);
  });
});
