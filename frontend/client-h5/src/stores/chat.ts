import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { sendMessage, sendAction, getSessionMessages } from '@/services/api'
import type { ChatMessage } from '@/types/chat'
import type { ChatRequest, ChatMessageView, QuickAction } from '@/types/api'
import { ERROR_CODES } from '@/types/api'

const SESSION_STORAGE_KEY = 'smartcs_session_id'
const DEFAULT_USER_ID = import.meta.env.VITE_USER_ID || 'u1001'

function generateId(): string {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
}

function loadSessionId(): string {
  const stored = localStorage.getItem(SESSION_STORAGE_KEY)
  if (stored) return stored
  const id = 's_' + generateId()
  localStorage.setItem(SESSION_STORAGE_KEY, id)
  return id
}

function toFrontendRole(role: string): 'user' | 'agent' | 'system' {
  switch (role) {
    case 'USER':
      return 'user'
    case 'AGENT':
    case 'HUMAN_AGENT':
      return 'agent'
    case 'SYSTEM':
      return 'system'
    default:
      return 'agent'
  }
}

function toChatMessage(view: ChatMessageView): ChatMessage {
  return {
    id: view.messageId,
    role: toFrontendRole(view.role),
    content: view.content || '',
    timestamp: new Date(view.createdAt).getTime(),
    status: 'sent',
    // Historical quickActions are not interactive — only live replies support click
    quickActions: undefined,
    agentReplyId: view.role !== 'USER' ? view.messageId : undefined,
  }
}

export const useChatStore = defineStore('chat', () => {
  const messages = ref<ChatMessage[]>([])
  const sessionId = ref(loadSessionId())
  const userId = ref(DEFAULT_USER_ID)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const lastAgentMessage = computed(() =>
    [...messages.value].reverse().find((m) => m.role === 'agent'),
  )

  function addMessage(msg: ChatMessage) {
    messages.value.push(msg)
  }

  function updateMessage(id: string, patch: Partial<ChatMessage>) {
    const idx = messages.value.findIndex((m) => m.id === id)
    if (idx !== -1) {
      messages.value[idx] = { ...messages.value[idx], ...patch }
    }
  }

  async function send(content: string) {
    const trimmed = content.trim()
    if (!trimmed || loading.value) return

    error.value = null

    const userMsg: ChatMessage = {
      id: generateId(),
      role: 'user',
      content: trimmed,
      timestamp: Date.now(),
      status: 'sending',
    }
    addMessage(userMsg)
    loading.value = true

    try {
      const request: ChatRequest = {
        sessionId: sessionId.value,
        userId: userId.value,
        channel: 'h5',
        content: trimmed,
      }

      const response = await sendMessage(request)

      updateMessage(userMsg.id, { status: 'sent' })

      if (response.code === ERROR_CODES.SUCCESS && response.data) {
        const reply = response.data

        // Persist session ID from server response
        if (reply.sessionId && reply.sessionId !== sessionId.value) {
          sessionId.value = reply.sessionId
          localStorage.setItem(SESSION_STORAGE_KEY, reply.sessionId)
        }

        const agentMsg: ChatMessage = {
          id: generateId(),
          role: 'agent',
          content: reply.content || '',
          timestamp: new Date(reply.createdAt).getTime(),
          status: 'sent',
          quickActions: reply.quickActions,
          agentReplyId: reply.replyId,
        }
        addMessage(agentMsg)
      } else {
        const agentMsg: ChatMessage = {
          id: generateId(),
          role: 'agent',
          content: response.message || '服务暂时不可用，请稍后重试',
          timestamp: Date.now(),
          status: 'sent',
        }
        addMessage(agentMsg)
        error.value = response.message
      }
    } catch (err) {
      updateMessage(userMsg.id, { status: 'failed', retryPayload: { content: trimmed, sessionId: sessionId.value } })
      error.value = err instanceof Error ? err.message : '网络错误，请检查连接'
    } finally {
      loading.value = false
    }
  }

  async function retry(messageId: string) {
    const msg = messages.value.find((m) => m.id === messageId)
    if (!msg?.retryPayload) return

    // Remove the failed message and re-send
    messages.value = messages.value.filter((m) => m.id !== messageId)
    await send(msg.retryPayload.content)
  }

  async function handleAction(action: QuickAction) {
    if (loading.value) return

    error.value = null

    // Show user's action choice as a message
    const userMsg: ChatMessage = {
      id: generateId(),
      role: 'user',
      content: action.label,
      timestamp: Date.now(),
      status: 'sending',
    }
    addMessage(userMsg)
    loading.value = true

    try {
      const response = await sendAction({
        sessionId: sessionId.value,
        userId: userId.value,
        channel: 'h5',
        actionId: action.value,
        actionType: action.actionType || action.value,
        content: action.label,
        payload: action.payload,
      })

      updateMessage(userMsg.id, { status: 'sent' })

      if (response.code === ERROR_CODES.SUCCESS && response.data) {
        const reply = response.data

        if (reply.sessionId && reply.sessionId !== sessionId.value) {
          sessionId.value = reply.sessionId
          localStorage.setItem(SESSION_STORAGE_KEY, reply.sessionId)
        }

        const agentMsg: ChatMessage = {
          id: generateId(),
          role: 'agent',
          content: reply.content || '',
          timestamp: new Date(reply.createdAt).getTime(),
          status: 'sent',
          quickActions: reply.quickActions,
          agentReplyId: reply.replyId,
        }
        addMessage(agentMsg)
      } else {
        const agentMsg: ChatMessage = {
          id: generateId(),
          role: 'agent',
          content: response.message || '操作失败，请稍后重试',
          timestamp: Date.now(),
          status: 'sent',
        }
        addMessage(agentMsg)
        error.value = response.message
      }
    } catch (err) {
      updateMessage(userMsg.id, { status: 'failed' })
      error.value = err instanceof Error ? err.message : '网络错误，请检查连接'
    } finally {
      loading.value = false
    }
  }

  async function loadHistory(): Promise<boolean> {
    loading.value = true
    error.value = null
    try {
      const response = await getSessionMessages(sessionId.value)
      if (response.code === ERROR_CODES.SUCCESS && response.data?.length) {
        messages.value = response.data.map(toChatMessage)
        return true
      }
      return false
    } catch {
      // Session not found or network error — start fresh
      return false
    } finally {
      loading.value = false
    }
  }

  function resetSession() {
    const id = 's_' + generateId()
    sessionId.value = id
    localStorage.setItem(SESSION_STORAGE_KEY, id)
    messages.value = []
    error.value = null
  }

  return {
    messages,
    sessionId,
    userId,
    loading,
    error,
    lastAgentMessage,
    send,
    retry,
    handleAction,
    loadHistory,
    resetSession,
  }
})
