import axios, { AxiosHeaders, type InternalAxiosRequestConfig } from "axios";
import {
  resolveAuthFailureReason,
  type AuthFailureReason,
} from "../auth/helpers";
import {
  clearAccessToken,
  getAccessToken,
  redirectToLogin,
  refreshAccessToken,
} from "../auth/tokenProvider";
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
import type { NotificationOutboxSummary } from "../types/notification";

const AUTH_ERROR_MESSAGES: Record<AuthFailureReason, string> = {
  expired: "登录已过期，请重新进入坐席工作台",
  forbidden: "当前坐席账号没有权限执行该操作",
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

type SmartCsRequestConfig = InternalAxiosRequestConfig & {
  _smartcsAuthRetried?: boolean;
};

const client = axios.create({
  baseURL,
  timeout: 15000,
  headers: {
    "Content-Type": "application/json; charset=UTF-8",
  },
});

let currentOperatorPromise: Promise<CurrentOperatorView> | null = null;

function authErrorMessage(code?: string, status?: number): string | null {
  const reason = resolveAuthFailureReason(code, status);
  return reason ? AUTH_ERROR_MESSAGES[reason] : null;
}

function handleAuthFailure(reason: AuthFailureReason): void {
  if (reason === "expired") {
    clearAccessToken();
  }
  redirectToLogin(reason);
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

client.interceptors.request.use(async (config) => {
  const headers = AxiosHeaders.from(config.headers);
  headers.set("X-SmartCS-Operator-Id", defaultOperatorId);
  headers.set("X-SmartCS-Roles", defaultRoles);

  const token = await getAccessToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  } else {
    headers.delete("Authorization");
  }

  config.headers = headers;
  return config;
});

client.interceptors.response.use(
  response => response,
  async (error) => {
    if (!axios.isAxiosError(error)) {
      return Promise.reject(error);
    }

    const status = error.response?.status;
    const data = error.response?.data as Partial<ApiResponse<unknown>> | undefined;
    const code = typeof data?.code === "string" ? data.code : undefined;
    const reason = resolveAuthFailureReason(code, status);
    const originalRequest = error.config as SmartCsRequestConfig | undefined;

    if (reason === "expired" && originalRequest && !originalRequest._smartcsAuthRetried) {
      originalRequest._smartcsAuthRetried = true;
      try {
        const token = await refreshAccessToken();
        if (token) {
          return client.request(originalRequest);
        }
      } catch {
        // 刷新失败后继续交给统一错误提示和登录跳转兜底。
      }
    }

    if (reason) {
      handleAuthFailure(reason);
    }

    return Promise.reject(error);
  },
);

async function unwrap<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  try {
    const { data } = await promise;
    if (data.code !== "0000") {
      const reason = resolveAuthFailureReason(data.code);
      if (reason) {
        handleAuthFailure(reason);
      }
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

export async function fetchNotificationOutboxSummary(): Promise<NotificationOutboxSummary> {
  return unwrap(client.get("/api/workbench/notifications/outbox/summary"));
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
