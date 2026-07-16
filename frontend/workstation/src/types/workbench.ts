// Types derived from docs/workbench-api.md
// Do not add fields not present in the API doc.

export interface ApiResponse<T> {
  code: string;
  message: string;
  data: T | null;
  traceId: string;
  metadata: Record<string, unknown>;
  timestamp: string;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  pageNo: number;
  pageSize: number;
}

export type WorkOrderStatus =
  | "PENDING"
  | "ASSIGNED"
  | "PROCESSING"
  | "APPROVED"
  | "REJECTED"
  | "RESOLVED"
  | "CLOSED"
  | "ESCALATED";

export type ApprovalStatus =
  | "PENDING"
  | "CLAIMED"
  | "APPROVED"
  | "REJECTED"
  | "CANCELLED"
  | "EXPIRED"
  | "ESCALATED";

export type TakeoverStatus =
  | "REQUESTED"
  | "QUEUED"
  | "ASSIGNED"
  | "IN_PROGRESS"
  | "RESOLVED"
  | "CANCELLED";

export type RouteDecision =
  | "AUTO_REPLY"
  | "AUTO_EXECUTE"
  | "CONFIRM_BEFORE_EXECUTE"
  | "HUMAN_REVIEW"
  | "HUMAN_TAKEOVER"
  | "REJECT";

export type RiskLevel = "L0" | "L1" | "L2" | "L3";
export type Priority = "LOW" | "NORMAL" | "HIGH" | "URGENT";
export type ApprovalType =
  | "REFUND"
  | "EXCHANGE"
  | "ADDRESS_CHANGE"
  | "ORDER_CANCEL"
  | "COMPENSATION"
  | "OTHER";
export type ApprovalDecisionType =
  | "APPROVED"
  | "REJECTED"
  | "REQUEST_MATERIALS"
  | "TRANSFER_TAKEOVER";

export interface TicketSummary {
  ticketId: string;
  traceId: string;
  sessionId: string;
  userId: string;
  intent: string;
  riskLevel: RiskLevel;
  routeDecision: RouteDecision;
  status: WorkOrderStatus;
  priority: Priority;
  assignedAgent?: string | null;
  reason?: string | null;
  slaDeadline?: string | null;
  createdAt: string;
  updatedAt: string;
  approvalId?: string | null;
  approvalType?: ApprovalType | null;
  approvalStatus?: ApprovalStatus | null;
  takeoverId?: string | null;
  takeoverStatus?: TakeoverStatus | null;
}

export interface TicketStatsView {
  total: number;
  pending: number;
  processing: number;
  completed: number;
  overdueRisk: number;
}

export interface TicketChangedEvent {
  eventId: string;
  ticketId: string;
  status: WorkOrderStatus;
  assignedAgent?: string | null;
  changedAt: string;
}

export interface CurrentOperatorView {
  operatorId: string;
  principalType: "AGENT" | "SUPERVISOR" | "ADMIN" | string;
  roles: string[];
  authSource: "STANDARD_HEADER" | "DEV_HEADER" | "LEGACY_BODY" | "DEV_FALLBACK" | string;
}

export interface WorkOrderView extends TicketSummary {
  contextSnapshot: Record<string, unknown>;
  resolution: Record<string, unknown>;
  resolvedAt?: string | null;
}

export interface ApprovalTaskView {
  approvalId: string;
  ticketId: string;
  traceId: string;
  sessionId: string;
  userId: string;
  intent: string;
  approvalType: string;
  riskLevel: string;
  routeDecision: RouteDecision;
  status: ApprovalStatus;
  priority: string;
  assignedReviewer?: string | null;
  riskReason?: string | null;
  requestPayload: Record<string, unknown>;
  contextSnapshot: Record<string, unknown>;
  approvalResult: Record<string, unknown>;
  expireAt?: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt?: string | null;
}

export interface HumanTakeoverView {
  takeoverId: string;
  ticketId?: string | null;
  traceId: string;
  sessionId: string;
  userId: string;
  triggerSource: string;
  status: TakeoverStatus;
  priority: string;
  assignedAgent?: string | null;
  reason?: string | null;
  contextSnapshot: Record<string, unknown>;
  startedAt?: string | null;
  endedAt?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface MessageView {
  messageId: string;
  traceId: string;
  sessionId: string;
  userId: string;
  role: "USER" | "AGENT" | "HUMAN_AGENT" | "SYSTEM";
  messageType: "TEXT" | "IMAGE" | "FILE" | "CARD" | "ACTION" | "SYSTEM";
  content?: string | null;
  quickActions: unknown[];
  intent?: string | null;
  riskLevel?: string | null;
  routeDecision?: RouteDecision | null;
  metadata: Record<string, unknown>;
  createdAt: string;
}

export interface ActionLogView {
  actionId: string;
  source: "WORK_ORDER" | "APPROVAL";
  ticketId: string;
  traceId: string;
  operatorId: string;
  actionType: string;
  beforeStatus?: string | null;
  afterStatus?: string | null;
  comment?: string | null;
  actionData: Record<string, unknown>;
  createdAt: string;
}

export interface TicketDetail {
  ticket: WorkOrderView;
  approval?: ApprovalTaskView | null;
  takeover?: HumanTakeoverView | null;
  messages: MessageView[];
  actions: ActionLogView[];
}

export interface ActionResult {
  ticketId: string;
  workOrderStatus: WorkOrderStatus;
  approvalStatus?: ApprovalStatus | null;
  takeoverStatus?: TakeoverStatus | null;
  message: string;
}

export interface OperatorActionRequest {
  operatorId?: string;
  comment?: string;
  payload?: Record<string, unknown>;
}

export interface ApprovalDecisionRequest {
  operatorId?: string;
  comment?: string;
  decisionType?: ApprovalDecisionType;
  result?: Record<string, unknown>;
}

export interface TakeoverFinishRequest {
  operatorId?: string;
  comment?: string;
  resolutionStatus?: "RESOLVED" | "CANCELLED";
  result?: Record<string, unknown>;
}

export interface TakeoverMessageRequest {
  operatorId?: string;
  content: string;
  payload?: Record<string, unknown>;
}

export interface InternalNoteRequest {
  operatorId?: string;
  comment: string;
  payload?: Record<string, unknown>;
}

export interface TicketQueryParams {
  status?: WorkOrderStatus;
  routeDecision?: RouteDecision;
  riskLevel?: RiskLevel;
  intent?: string;
  priority?: Priority;
  assignedAgent?: string;
  keyword?: string;
  createdAtFrom?: string;
  createdAtTo?: string;
  pageNo?: number;
  pageSize?: number;
}
