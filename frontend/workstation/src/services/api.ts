import axios from "axios";
import type {
  ApiResponse,
  PageResult,
  TicketSummary,
  TicketStatsView,
  CurrentOperatorView,
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

const AUTH_ERROR_MESSAGES: Record<string, string> = {
  "1002": "登录已过期，请重新进入坐席工作台",
  "1003": "当前坐席账号没有权限执行该操作",
};

export class WorkbenchApiError extends Error {
  constructor(
    message: string,
    public readonly code?: string,
    public readonly status?: number,
  ) {
    super(message);
    this.name = "WorkbenchApiError";
  }
}

const baseURL =
  import.meta.env.VITE_WORKSTATION_API_BASE_URL || "http://localhost:8083";
const defaultOperatorId =
  import.meta.env.VITE_WORKSTATION_OPERATOR_ID || "agent_001";
const defaultRoles =
  import.meta.env.VITE_WORKSTATION_ROLES || "AGENT";
const authToken = import.meta.env.VITE_WORKSTATION_AUTH_TOKEN || "";

const client = axios.create({
  baseURL,
  timeout: 15000,
  headers: {
    "Content-Type": "application/json; charset=UTF-8",
    "X-SmartCS-Operator-Id": defaultOperatorId,
    "X-SmartCS-Roles": defaultRoles,
    ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
  },
});

let currentOperatorPromise: Promise<CurrentOperatorView> | null = null;

function authErrorMessage(code?: string, status?: number): string | null {
  if (code && AUTH_ERROR_MESSAGES[code]) {
    return AUTH_ERROR_MESSAGES[code];
  }
  if (status === 401) return AUTH_ERROR_MESSAGES["1002"];
  if (status === 403) return AUTH_ERROR_MESSAGES["1003"];
  return null;
}

function apiResponseError<T>(data: ApiResponse<T>, status?: number): WorkbenchApiError {
  return new WorkbenchApiError(
    authErrorMessage(data.code, status) || data.message || `API error ${data.code}`,
    data.code,
    status,
  );
}

function normalizeError(error: unknown): Error {
  if (error instanceof WorkbenchApiError) {
    return error;
  }
  if (axios.isAxiosError(error)) {
    const status = error.response?.status;
    const data = error.response?.data as Partial<ApiResponse<unknown>> | undefined;
    const code = typeof data?.code === "string" ? data.code : undefined;
    const message = authErrorMessage(code, status)
      || (typeof data?.message === "string" && data.message)
      || error.message;
    return new WorkbenchApiError(message, code, status);
  }
  return error instanceof Error ? error : new Error("请求失败，请稍后重试");
}

async function unwrap<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  try {
    const { data } = await promise;
    if (data.code !== "0000") {
      throw apiResponseError(data);
    }
    if (data.data === null) {
      throw new Error("Empty response data");
    }
    return data.data;
  } catch (error) {
    throw normalizeError(error);
  }
}

export async function fetchCurrentOperator(
  options: { force?: boolean } = {},
): Promise<CurrentOperatorView> {
  if (!currentOperatorPromise || options.force) {
    const promise = unwrap<CurrentOperatorView>(client.get("/api/workbench/me"))
      .catch((error) => {
        currentOperatorPromise = null;
        throw error;
      });
    currentOperatorPromise = promise;
  }
  return currentOperatorPromise;
}

async function withCurrentOperator<T extends { operatorId?: string }>(
  body: T,
): Promise<T & { operatorId: string }> {
  const operator = await fetchCurrentOperator();
  return {
    ...body,
    operatorId: operator.operatorId || body.operatorId || defaultOperatorId,
  };
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
  const payload = await withCurrentOperator(body);
  return unwrap(
    client.post(`/api/workbench/tickets/${ticketId}/notes`, payload),
  );
}

export async function claimTicket(
  ticketId: string,
  body: OperatorActionRequest,
): Promise<ActionResult> {
  const payload = await withCurrentOperator(body);
  return unwrap(
    client.post(`/api/workbench/tickets/${ticketId}/claim`, payload),
  );
}

export async function approveTicket(
  ticketId: string,
  body: ApprovalDecisionRequest,
): Promise<ActionResult> {
  const payload = await withCurrentOperator(body);
  return unwrap(
    client.post(`/api/workbench/tickets/${ticketId}/approval/approve`, payload),
  );
}

export async function rejectTicket(
  ticketId: string,
  body: ApprovalDecisionRequest,
): Promise<ActionResult> {
  const payload = await withCurrentOperator(body);
  return unwrap(
    client.post(`/api/workbench/tickets/${ticketId}/approval/reject`, payload),
  );
}

export async function requestApprovalMaterials(
  ticketId: string,
  body: ApprovalDecisionRequest,
): Promise<ActionResult> {
  const payload = await withCurrentOperator(body);
  return unwrap(
    client.post(
      `/api/workbench/tickets/${ticketId}/approval/request-materials`,
      payload,
    ),
  );
}

export async function transferApprovalToTakeover(
  ticketId: string,
  body: ApprovalDecisionRequest,
): Promise<ActionResult> {
  const payload = await withCurrentOperator(body);
  return unwrap(
    client.post(
      `/api/workbench/tickets/${ticketId}/approval/transfer-takeover`,
      payload,
    ),
  );
}

export async function startTakeover(
  ticketId: string,
  body: OperatorActionRequest,
): Promise<ActionResult> {
  const payload = await withCurrentOperator(body);
  return unwrap(
    client.post(`/api/workbench/tickets/${ticketId}/takeover/start`, payload),
  );
}

export async function finishTakeover(
  ticketId: string,
  body: TakeoverFinishRequest,
): Promise<ActionResult> {
  const payload = await withCurrentOperator(body);
  return unwrap(
    client.post(`/api/workbench/tickets/${ticketId}/takeover/finish`, payload),
  );
}

export async function sendTakeoverMessage(
  ticketId: string,
  body: TakeoverMessageRequest,
): Promise<ActionResult> {
  const payload = await withCurrentOperator(body);
  return unwrap(
    client.post(`/api/workbench/tickets/${ticketId}/takeover/messages`, payload),
  );
}
