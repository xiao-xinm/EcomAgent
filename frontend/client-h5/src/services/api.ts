import axios, { AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import {
  resolveAuthFailureReason,
  type AuthFailureReason,
} from '@/auth/helpers'
import {
  clearAccessToken,
  getAccessToken,
  redirectToLogin,
  refreshAccessToken,
} from '@/auth/tokenProvider'
import type {
  ApiResponse,
  ChatRequest,
  ChatActionRequest,
  AgentReply,
  ChatSessionView,
  ChatMessageView,
} from '@/types/api'
import { runtimeChatConfig } from '@/config/runtime'

const AUTH_ERROR_MESSAGES: Record<AuthFailureReason, string> = {
  expired: '登录已过期，请重新进入客服页面',
  forbidden: '当前账号无权访问该会话',
}

export class ChatApiError extends Error {
  constructor(
    message: string,
    public readonly code?: string,
    public readonly status?: number,
    public readonly authFailureReason?: AuthFailureReason,
  ) {
    super(message)
    this.name = 'ChatApiError'
  }
}

type SmartCsRequestConfig = InternalAxiosRequestConfig & {
  _smartcsAuthRetried?: boolean
}

const client = axios.create({
  baseURL: runtimeChatConfig.apiBaseUrl,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8',
  },
  timeout: 15_000,
})

function authErrorMessage(code?: string, status?: number): string | null {
  const reason = resolveAuthFailureReason(code, status)
  return reason ? AUTH_ERROR_MESSAGES[reason] : null
}

function handleBusinessAuthFailure(response: Pick<ApiResponse<unknown>, 'code'>): void {
  const reason = resolveAuthFailureReason(response.code)
  if (reason) {
    handleAuthFailure(reason)
  }
}

function handleAuthFailure(reason: AuthFailureReason): void {
  if (reason === 'expired') {
    clearAccessToken()
  }
  redirectToLogin(reason)
}

export function chatResponseErrorMessage(
  response: Pick<ApiResponse<unknown>, 'code' | 'message'>,
  fallback: string,
): string {
  return authErrorMessage(response.code) || response.message || fallback
}

export function chatRequestErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ChatApiError) {
    return error.message
  }
  if (error instanceof Error) {
    return error.message
  }
  return fallback
}

client.interceptors.request.use(async config => {
  const headers = AxiosHeaders.from(config.headers)
  headers.set('X-SmartCS-User-Id', runtimeChatConfig.userId)
  headers.set('X-SmartCS-Roles', runtimeChatConfig.userRoles)

  const token = await getAccessToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  } else {
    headers.delete('Authorization')
  }

  config.headers = headers
  return config
})

client.interceptors.response.use(
  response => response,
  async error => {
    if (axios.isAxiosError(error)) {
      const status = error.response?.status
      const data = error.response?.data as Partial<ApiResponse<unknown>> | undefined
      const code = typeof data?.code === 'string' ? data.code : undefined
      const reason = resolveAuthFailureReason(code, status)
      const originalRequest = error.config as SmartCsRequestConfig | undefined

      if (reason === 'expired' && originalRequest && !originalRequest._smartcsAuthRetried) {
        originalRequest._smartcsAuthRetried = true
        try {
          const token = await refreshAccessToken()
          if (token) {
            return client.request(originalRequest)
          }
        } catch {
          // 刷新失败后继续走统一的登录跳转和错误提示。
        }
      }

      if (reason) {
        handleAuthFailure(reason)
      }

      const message = authErrorMessage(code, status)
        || (typeof data?.message === 'string' && data.message)
        || error.message
      return Promise.reject(new ChatApiError(message, code, status, reason || undefined))
    }
    return Promise.reject(error)
  },
)

export async function sendMessage(request: ChatRequest): Promise<ApiResponse<AgentReply>> {
  const { data } = await client.post<ApiResponse<AgentReply>>('/api/chat/messages', request)
  handleBusinessAuthFailure(data)
  return data
}

export async function sendAction(request: ChatActionRequest): Promise<ApiResponse<AgentReply>> {
  const { data } = await client.post<ApiResponse<AgentReply>>('/api/chat/actions', request)
  handleBusinessAuthFailure(data)
  return data
}

export async function getSession(sessionId: string): Promise<ApiResponse<ChatSessionView>> {
  const { data } = await client.get<ApiResponse<ChatSessionView>>(
    `/api/chat/sessions/${sessionId}`,
  )
  handleBusinessAuthFailure(data)
  return data
}

export async function getSessionMessages(
  sessionId: string,
  limit = 100,
): Promise<ApiResponse<ChatMessageView[]>> {
  const { data } = await client.get<ApiResponse<ChatMessageView[]>>(
    `/api/chat/sessions/${sessionId}/messages`,
    { params: { limit } },
  )
  handleBusinessAuthFailure(data)
  return data
}

export function createSessionEventSource(
  sessionId: string,
  options: { lastMessageId?: string; limit?: number } = {},
): EventSource {
  const baseUrl = runtimeChatConfig.apiBaseUrl.replace(/\/$/, '')
  const url = new URL(
    `${baseUrl}/api/chat/sessions/${encodeURIComponent(sessionId)}/events`,
    window.location.origin,
  )
  if (options.lastMessageId) {
    url.searchParams.set('lastMessageId', options.lastMessageId)
  }
  if (options.limit) {
    url.searchParams.set('limit', String(options.limit))
  }
  return new EventSource(url.toString())
}
