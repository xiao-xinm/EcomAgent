import axios from 'axios'
import type {
  ApiResponse,
  ChatRequest,
  ChatActionRequest,
  AgentReply,
  ChatSessionView,
  ChatMessageView,
} from '@/types/api'
import { runtimeChatConfig } from '@/config/runtime'

const AUTH_ERROR_MESSAGES: Record<string, string> = {
  '1002': '登录已过期，请重新进入客服页面',
  '1003': '当前账号无权访问该会话',
}

export class ChatApiError extends Error {
  constructor(
    message: string,
    public readonly code?: string,
    public readonly status?: number,
  ) {
    super(message)
    this.name = 'ChatApiError'
  }
}

const client = axios.create({
  baseURL: runtimeChatConfig.apiBaseUrl,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8',
    'X-SmartCS-User-Id': runtimeChatConfig.userId,
    'X-SmartCS-Roles': runtimeChatConfig.userRoles,
    ...(runtimeChatConfig.authToken
      ? { Authorization: `Bearer ${runtimeChatConfig.authToken}` }
      : {}),
  },
  timeout: 15_000,
})

function authErrorMessage(code?: string, status?: number): string | null {
  if (code && AUTH_ERROR_MESSAGES[code]) {
    return AUTH_ERROR_MESSAGES[code]
  }
  if (status === 401) return AUTH_ERROR_MESSAGES['1002']
  if (status === 403) return AUTH_ERROR_MESSAGES['1003']
  return null
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

client.interceptors.response.use(
  response => response,
  error => {
    if (axios.isAxiosError(error)) {
      const status = error.response?.status
      const data = error.response?.data as Partial<ApiResponse<unknown>> | undefined
      const code = typeof data?.code === 'string' ? data.code : undefined
      const message = authErrorMessage(code, status)
        || (typeof data?.message === 'string' && data.message)
        || error.message
      return Promise.reject(new ChatApiError(message, code, status))
    }
    return Promise.reject(error)
  },
)

export async function sendMessage(request: ChatRequest): Promise<ApiResponse<AgentReply>> {
  const { data } = await client.post<ApiResponse<AgentReply>>('/api/chat/messages', request)
  return data
}

export async function sendAction(request: ChatActionRequest): Promise<ApiResponse<AgentReply>> {
  const { data } = await client.post<ApiResponse<AgentReply>>('/api/chat/actions', request)
  return data
}

export async function getSession(sessionId: string): Promise<ApiResponse<ChatSessionView>> {
  const { data } = await client.get<ApiResponse<ChatSessionView>>(
    `/api/chat/sessions/${sessionId}`,
  )
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
