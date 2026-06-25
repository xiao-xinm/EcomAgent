import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { sendMessage, sendAction, getSessionMessages } from '@/services/api'
import type { ChatMessage } from '@/types/chat'
import type { ChatRequest, ChatMessageView, QuickAction } from '@/types/api'
import { ERROR_CODES } from '@/types/api'

const SESSION_STORAGE_KEY = 'smartcs_session_id'
const DEFAULT_USER_ID = import.meta.env.VITE_USER_ID || 'u1001'
const DEFAULT_CHANNEL = import.meta.env.VITE_CHANNEL || 'h5'
const POLLING_INTERVAL_MS = Number(import.meta.env.VITE_CHAT_POLLING_INTERVAL_MS || 3000)

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

function normalizeQuickActions(actions: unknown[] | null | undefined): QuickAction[] | undefined {
  if (!Array.isArray(actions)) return undefined

  const quickActions = actions.filter((action): action is QuickAction => {
    if (!action || typeof action !== 'object') return false
    const candidate = action as Partial<QuickAction>
    return typeof candidate.label === 'string' && typeof candidate.value === 'string'
  })
  return quickActions.length ? quickActions : undefined
}

function metadataString(metadata: Record<string, unknown> | undefined, key: string): string | null {
  const value = metadata?.[key]
  return typeof value === 'string' && value ? value : null
}

function toChatMessage(view: ChatMessageView, existing?: ChatMessage): ChatMessage {
  return {
    id: view.messageId,
    role: toFrontendRole(view.role),
    backendRole: view.role,
    messageType: view.messageType,
    content: view.content || '',
    timestamp: new Date(view.createdAt).getTime(),
    status: 'sent',
    intent: view.intent || metadataString(view.metadata, 'intent') || existing?.intent,
    riskLevel: view.riskLevel || existing?.riskLevel,
    routeDecision: view.routeDecision || existing?.routeDecision,
    metadata: view.metadata || existing?.metadata || {},
    // 远端刷新时保留已展示的快捷动作，避免轮询把“确认/取消”按钮刷没。
    quickActions: existing?.quickActions || normalizeQuickActions(view.quickActions),
    agentReplyId: view.role !== 'USER' ? view.messageId : undefined,
  }
}

export const useChatStore = defineStore('chat', () => {
  const messages = ref<ChatMessage[]>([])
  const sessionId = ref(loadSessionId())
  const userId = ref(DEFAULT_USER_ID)
  const loading = ref(false)
  const syncing = ref(false)
  const error = ref<string | null>(null)
  let pollingTimer: number | undefined
  let visibilityListenerBound = false

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

  function mergeRemoteMessages(remoteMessages: ChatMessage[]) {
    const remoteIds = new Set(remoteMessages.map((m) => m.id))
    const pendingLocalMessages = messages.value.filter(
      (msg) => msg.status !== 'sent' && !remoteIds.has(msg.id),
    )
    messages.value = [...remoteMessages, ...pendingLocalMessages].sort(
      (a, b) => a.timestamp - b.timestamp,
    )
  }

  async function syncMessages(
    options: { silent?: boolean; force?: boolean; suppressError?: boolean } = {},
  ): Promise<boolean> {
    if (syncing.value) return false
    if (loading.value && !options.force) return false

    syncing.value = true
    if (!options.silent) {
      loading.value = true
      error.value = null
    }

    try {
      const response = await getSessionMessages(sessionId.value)
      if (response.code === ERROR_CODES.SUCCESS && response.data) {
        const byMessageId = new Map(messages.value.map((msg) => [msg.id, msg]))
        const byReplyId = new Map(
          messages.value
            .filter((msg) => msg.agentReplyId)
            .map((msg) => [msg.agentReplyId as string, msg]),
        )
        const remoteMessages = response.data.map((view) =>
          toChatMessage(view, byMessageId.get(view.messageId) || byReplyId.get(view.messageId)),
        )
        mergeRemoteMessages(remoteMessages)
        return remoteMessages.length > 0
      }
      return false
    } catch (err) {
      if (!options.silent && !options.suppressError) {
        error.value = err instanceof Error ? err.message : '消息同步失败'
      }
      return false
    } finally {
      syncing.value = false
      if (!options.silent) {
        loading.value = false
      }
    }
  }

  function syncWhenVisible() {
    if (document.visibilityState === 'visible') {
      void syncMessages({ silent: true })
    }
  }

  function startPolling() {
    if (pollingTimer) return
    pollingTimer = window.setInterval(syncWhenVisible, POLLING_INTERVAL_MS)
    if (!visibilityListenerBound) {
      document.addEventListener('visibilitychange', syncWhenVisible)
      visibilityListenerBound = true
    }
  }

  function stopPolling() {
    if (pollingTimer) {
      window.clearInterval(pollingTimer)
      pollingTimer = undefined
    }
    if (visibilityListenerBound) {
      document.removeEventListener('visibilitychange', syncWhenVisible)
      visibilityListenerBound = false
    }
  }

  async function send(content: string) {
    const trimmed = content.trim()
    if (!trimmed || loading.value) return

    error.value = null

    const userMsg: ChatMessage = {
      id: generateId(),
      role: 'user',
      backendRole: 'USER',
      messageType: 'TEXT',
      content: trimmed,
      timestamp: Date.now(),
      status: 'sending',
      metadata: { channel: DEFAULT_CHANNEL },
    }
    addMessage(userMsg)
    loading.value = true

    try {
      const request: ChatRequest = {
        sessionId: sessionId.value,
        userId: userId.value,
        channel: DEFAULT_CHANNEL,
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
          backendRole: 'AGENT',
          messageType: reply.messageType,
          content: reply.content || '',
          timestamp: new Date(reply.createdAt).getTime(),
          status: 'sent',
          intent: metadataString(reply.metadata, 'intent'),
          riskLevel: reply.riskLevel,
          routeDecision: reply.routeDecision,
          metadata: reply.metadata || {},
          quickActions: reply.quickActions,
          agentReplyId: reply.replyId,
        }
        addMessage(agentMsg)
        await syncMessages({ silent: true, force: true })
      } else {
        const agentMsg: ChatMessage = {
          id: generateId(),
          role: 'agent',
          backendRole: 'AGENT',
          messageType: 'TEXT',
          content: response.message || '服务暂时不可用，请稍后重试',
          timestamp: Date.now(),
          status: 'sent',
          metadata: {},
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

  async function handleAction(
    action: QuickAction,
    options: { content?: string; payload?: Record<string, unknown> } = {},
  ) {
    if (loading.value) return

    error.value = null

    // Show user's action choice as a message
    const userMsg: ChatMessage = {
      id: generateId(),
      role: 'user',
      backendRole: 'USER',
      messageType: 'ACTION',
      content: options.content || action.label,
      timestamp: Date.now(),
      status: 'sending',
      metadata: { actionId: action.value, actionType: action.actionType || action.value },
    }
    addMessage(userMsg)
    loading.value = true

    try {
      const response = await sendAction({
        sessionId: sessionId.value,
        userId: userId.value,
        channel: DEFAULT_CHANNEL,
        actionId: action.value,
        actionType: action.actionType || action.value,
        content: options.content || action.label,
        payload: options.payload || action.payload,
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
          backendRole: 'AGENT',
          messageType: reply.messageType,
          content: reply.content || '',
          timestamp: new Date(reply.createdAt).getTime(),
          status: 'sent',
          intent: metadataString(reply.metadata, 'intent'),
          riskLevel: reply.riskLevel,
          routeDecision: reply.routeDecision,
          metadata: reply.metadata || {},
          quickActions: reply.quickActions,
          agentReplyId: reply.replyId,
        }
        addMessage(agentMsg)
        await syncMessages({ silent: true, force: true })
      } else {
        const agentMsg: ChatMessage = {
          id: generateId(),
          role: 'agent',
          backendRole: 'AGENT',
          messageType: 'TEXT',
          content: response.message || '操作失败，请稍后重试',
          timestamp: Date.now(),
          status: 'sent',
          metadata: {},
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
    return syncMessages({ suppressError: true })
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
    syncing,
    error,
    lastAgentMessage,
    send,
    retry,
    handleAction,
    loadHistory,
    syncMessages,
    startPolling,
    stopPolling,
    resetSession,
  }
})
