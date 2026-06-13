/**
 * Backend API types — mirror of smartcs-common domain records.
 */

export interface ApiResponse<T> {
  code: string
  message: string
  data: T | null
  traceId: string
  metadata: Record<string, unknown>
  timestamp: string
}

// --- ChatRequest (POST /api/chat/messages) ---

export interface ChatRequest {
  traceId?: string
  sessionId?: string
  userId: string
  channel?: string
  content: string
  metadata?: Record<string, unknown>
}

// --- ChatActionRequest (POST /api/chat/actions) ---

export interface ChatActionRequest {
  traceId?: string
  sessionId: string
  userId: string
  channel?: string
  actionId?: string
  actionType: string
  content?: string
  payload?: Record<string, unknown>
  metadata?: Record<string, unknown>
}

// --- AgentReply ---

export type MessageType = 'TEXT' | 'IMAGE' | 'FILE' | 'CARD' | 'ACTION' | 'SYSTEM'

export type RiskLevel = 'L0' | 'L1' | 'L2' | 'L3'

export type RouteDecision =
  | 'AUTO_REPLY'
  | 'AUTO_EXECUTE'
  | 'CONFIRM_BEFORE_EXECUTE'
  | 'HUMAN_REVIEW'
  | 'HUMAN_TAKEOVER'
  | 'REJECT'

export interface QuickAction {
  label: string
  value: string
  actionType?: string
  payload?: Record<string, unknown>
}

export interface AgentReply {
  replyId: string
  traceId: string
  sessionId: string
  messageType: MessageType
  content?: string
  quickActions?: QuickAction[]
  riskLevel: RiskLevel
  routeDecision: RouteDecision
  ticketId?: string
  metadata?: Record<string, unknown>
  createdAt: string
}

// --- ChatSessionView (GET /api/chat/sessions/{sessionId}) ---

export type SessionStatus = 'ACTIVE' | 'HUMAN_TAKEOVER' | 'TIMEOUT' | 'CLOSED' | string

export type DialogState =
  | 'INIT'
  | 'INTENT_RECOGNIZED'
  | 'SLOT_FILLING'
  | 'READY_TO_EXECUTE'
  | 'WAITING_CONFIRM'
  | 'EXECUTING'
  | 'EXECUTED'
  | 'COMPLETED'
  | 'HUMAN_REVIEW'
  | 'HUMAN_TAKEOVER'
  | 'TIMEOUT'
  | 'CLOSED'
  | string

export interface ChatSessionView {
  sessionId: string
  traceId: string
  userId: string
  channel: string
  status: SessionStatus
  dialogState: DialogState
  currentIntent?: string | null
  slots: Record<string, unknown>
  contextSnapshot: Record<string, unknown>
  lastMessageAt?: string | null
  createdAt: string
  updatedAt: string
  closedAt?: string | null
}

// --- ChatMessageView (GET /api/chat/sessions/{sessionId}/messages) ---

export type MessageRole = 'USER' | 'AGENT' | 'HUMAN_AGENT' | 'SYSTEM' | string

export interface ChatMessageView {
  messageId: string
  traceId: string
  sessionId: string
  userId: string
  role: MessageRole
  messageType: MessageType | string
  content?: string | null
  attachments: unknown[]
  quickActions: unknown[]
  intent?: string | null
  riskLevel?: RiskLevel | null
  routeDecision?: RouteDecision | null
  metadata: Record<string, unknown>
  createdAt: string
}

// --- Error codes ---

export const ERROR_CODES = {
  SUCCESS: '0000',
  BAD_REQUEST: '1001',
  UNAUTHORIZED: '1002',
  FORBIDDEN: '1003',
  NOT_FOUND: '1004',
  RATE_LIMITED: '1005',
  INTERNAL_ERROR: '1006',
  AGENT_LOW_CONFIDENCE: '2001',
  RISK_REVIEW_REQUIRED: '2002',
  RISK_TAKEOVER_REQUIRED: '2003',
  SKILL_NOT_FOUND: '3001',
  SKILL_EXECUTION_FAILED: '3002',
  TICKET_NOT_FOUND: '4001',
  TICKET_ACTION_REJECTED: '4002',
} as const
