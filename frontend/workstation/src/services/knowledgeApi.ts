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
import type { ApiResponse } from "../types/workbench";
import type {
  FaqItem,
  FaqPageResult,
  FaqQueryParams,
  FaqStatus,
  FaqStatusRequest,
  FaqUpsertRequest,
} from "../types/knowledge";

const AUTH_ERROR_MESSAGES: Record<AuthFailureReason, string> = {
  expired: "登录已过期，请重新进入坐席工作台",
  forbidden: "当前坐席账号没有权限访问知识库",
};

class KnowledgeApiError extends Error {
  constructor(
    message: string,
    public readonly code?: string,
    public readonly status?: number,
  ) {
    super(message);
    this.name = "KnowledgeApiError";
  }
}

type SmartCsRequestConfig = InternalAxiosRequestConfig & {
  _smartcsAuthRetried?: boolean;
};

const knowledgeBaseURL =
  import.meta.env.VITE_WORKSTATION_KNOWLEDGE_API_BASE_URL || "http://localhost:8084";
const defaultOperatorId =
  import.meta.env.VITE_WORKSTATION_OPERATOR_ID || "agent_001";
const defaultRoles =
  import.meta.env.VITE_WORKSTATION_ROLES || "AGENT";

const knowledgeClient = axios.create({
  baseURL: knowledgeBaseURL,
  timeout: 15000,
  headers: {
    "Content-Type": "application/json; charset=UTF-8",
  },
});

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

function normalizeError(error: unknown): Error {
  if (error instanceof KnowledgeApiError) {
    return error;
  }
  if (axios.isAxiosError(error)) {
    const status = error.response?.status;
    const data = error.response?.data as Partial<ApiResponse<unknown>> | undefined;
    const code = typeof data?.code === "string" ? data.code : undefined;
    const message = authErrorMessage(code, status)
      || (typeof data?.message === "string" && data.message)
      || error.message;
    return new KnowledgeApiError(message, code, status);
  }
  return error instanceof Error ? error : new Error("知识库请求失败，请稍后重试");
}

knowledgeClient.interceptors.request.use(async (config) => {
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

knowledgeClient.interceptors.response.use(
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
          return knowledgeClient.request(originalRequest);
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
      throw new KnowledgeApiError(
        authErrorMessage(data.code) || data.message || `API error ${data.code}`,
        data.code,
      );
    }
    if (data.data === null) {
      throw new Error("Empty response data");
    }
    return data.data;
  } catch (error) {
    throw normalizeError(error);
  }
}

export async function fetchFaqs(params: FaqQueryParams): Promise<FaqPageResult> {
  return unwrap(knowledgeClient.get("/api/knowledge/faq", { params }));
}

export async function createFaq(body: FaqUpsertRequest): Promise<FaqItem> {
  return unwrap(knowledgeClient.post("/api/knowledge/faq", body));
}

export async function updateFaq(
  faqId: string,
  body: FaqUpsertRequest,
): Promise<FaqItem> {
  return unwrap(knowledgeClient.put(`/api/knowledge/faq/${faqId}`, body));
}

export async function updateFaqStatus(
  faqId: string,
  status: FaqStatus,
): Promise<FaqItem> {
  const body: FaqStatusRequest = { status };
  return unwrap(knowledgeClient.post(`/api/knowledge/faq/${faqId}/status`, body));
}
