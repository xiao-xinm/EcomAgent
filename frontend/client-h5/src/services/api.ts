import axios from 'axios'
import type {
  ApiResponse,
  ChatRequest,
  ChatActionRequest,
  AgentReply,
  ChatSessionView,
  ChatMessageView,
} from '@/types/api'

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  headers: { 'Content-Type': 'application/json;charset=UTF-8' },
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
