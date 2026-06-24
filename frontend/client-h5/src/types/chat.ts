import type {
  MessageRole as BackendMessageRole,
  MessageType,
  QuickAction,
  RiskLevel,
  RouteDecision,
} from './api'

/**
 * Frontend chat message — wraps both user-sent and agent-received messages.
 */
export type MessageRole = 'user' | 'agent' | 'system'

export type MessageStatus = 'sending' | 'sent' | 'failed'

export interface ChatMessage {
  id: string
  role: MessageRole
  /** Raw backend role, used to distinguish AI, system, and human agent messages. */
  backendRole?: BackendMessageRole
  messageType?: MessageType | string
  content: string
  timestamp: number
  status: MessageStatus
  intent?: string | null
  riskLevel?: RiskLevel | null
  routeDecision?: RouteDecision | null
  metadata?: Record<string, unknown>
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
