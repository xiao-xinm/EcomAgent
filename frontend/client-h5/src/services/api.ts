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
