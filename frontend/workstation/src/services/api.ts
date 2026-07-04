import axios from "axios";
import type {
  ApiResponse,
  PageResult,
  TicketSummary,
  TicketStatsView,
  TicketDetail,
  ActionResult,
  OperatorActionRequest,
  ApprovalDecisionRequest,
  TakeoverFinishRequest,
  TakeoverMessageRequest,
  InternalNoteRequest,
  TicketQueryParams,
  ActionLogView,
} from "../types/workbench";

const baseURL =
  import.meta.env.VITE_WORKSTATION_API_BASE_URL || "http://localhost:8083";

const client = axios.create({
  baseURL,
  timeout: 15000,
  headers: { "Content-Type": "application/json; charset=UTF-8" },
});

async function unwrap<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const { data } = await promise;
  if (data.code !== "0000") {
    throw new Error(data.message || `API error ${data.code}`);
  }
  if (data.data === null) {
    throw new Error("Empty response data");
  }
  return data.data;
}

export async function fetchTickets(
  params: TicketQueryParams,
): Promise<PageResult<TicketSummary>> {
  return unwrap(client.get("/api/workbench/tickets", { params }));
}

export async function fetchTicketStats(): Promise<TicketStatsView> {
  return unwrap(client.get("/api/workbench/tickets/stats"));
}

export async function fetchTicketDetail(
  ticketId: string,
): Promise<TicketDetail> {
  return unwrap(client.get(`/api/workbench/tickets/${ticketId}`));
}

export async function fetchTicketActions(
  ticketId: string,
): Promise<ActionLogView[]> {
  return unwrap(client.get(`/api/workbench/tickets/${ticketId}/actions`));
}

export async function addInternalNote(
  ticketId: string,
  body: InternalNoteRequest,
): Promise<ActionResult> {
  return unwrap(
    client.post(`/api/workbench/tickets/${ticketId}/notes`, body),
  );
}

export async function claimTicket(
  ticketId: string,
  body: OperatorActionRequest,
): Promise<ActionResult> {
  return unwrap(
    client.post(`/api/workbench/tickets/${ticketId}/claim`, body),
  );
}

export async function approveTicket(
  ticketId: string,
  body: ApprovalDecisionRequest,
): Promise<ActionResult> {
  return unwrap(
    client.post(`/api/workbench/tickets/${ticketId}/approval/approve`, body),
  );
}

export async function rejectTicket(
  ticketId: string,
  body: ApprovalDecisionRequest,
): Promise<ActionResult> {
  return unwrap(
    client.post(`/api/workbench/tickets/${ticketId}/approval/reject`, body),
  );
}

export async function startTakeover(
  ticketId: string,
  body: OperatorActionRequest,
): Promise<ActionResult> {
  return unwrap(
    client.post(`/api/workbench/tickets/${ticketId}/takeover/start`, body),
  );
}

export async function finishTakeover(
  ticketId: string,
  body: TakeoverFinishRequest,
): Promise<ActionResult> {
  return unwrap(
    client.post(`/api/workbench/tickets/${ticketId}/takeover/finish`, body),
  );
}

export async function sendTakeoverMessage(
  ticketId: string,
  body: TakeoverMessageRequest,
): Promise<ActionResult> {
  return unwrap(
    client.post(`/api/workbench/tickets/${ticketId}/takeover/messages`, body),
  );
}
