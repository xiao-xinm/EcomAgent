import type { QuickAction } from './api'

/**
 * Frontend chat message — wraps both user-sent and agent-received messages.
 */
export type MessageRole = 'user' | 'agent' | 'system'

export type MessageStatus = 'sending' | 'sent' | 'failed'

export interface ChatMessage {
  id: string
  role: MessageRole
  content: string
  timestamp: number
  status: MessageStatus
  /** Agent-only: quick action buttons returned by backend */
  quickActions?: QuickAction[]
  /** Agent-only: raw agent reply for reference */
  agentReplyId?: string
  /** User-only: stored request body for retry */
  retryPayload?: {
    content: string
    sessionId?: string
  }
}
