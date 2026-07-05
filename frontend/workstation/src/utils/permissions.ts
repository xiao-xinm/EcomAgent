import type {
  CurrentOperatorView,
  TicketDetail,
  TicketSummary,
} from "../types/workbench";

export interface PermissionCheck {
  allowed: boolean;
  reason?: string;
}

const WORKSTATION_ROLES = new Set(["AGENT", "SUPERVISOR", "ADMIN"]);
const SUPERVISOR_ROLES = new Set(["SUPERVISOR", "ADMIN"]);
const TERMINAL_TICKET_STATUSES = new Set(["APPROVED", "REJECTED", "RESOLVED", "CLOSED"]);
const REVIEWABLE_APPROVAL_STATUSES = new Set(["PENDING", "CLAIMED"]);
const STARTABLE_TAKEOVER_STATUSES = new Set(["REQUESTED", "QUEUED", "ASSIGNED"]);

function deny(reason: string): PermissionCheck {
  return { allowed: false, reason };
}

function allow(): PermissionCheck {
  return { allowed: true };
}

function normalizedOperatorRoles(operator?: CurrentOperatorView | null): Set<string> {
  const values = [
    operator?.principalType,
    ...(operator?.roles || []),
  ]
    .filter(Boolean)
    .map((role) => String(role).trim().toUpperCase())
    .filter(Boolean);
  return new Set(values);
}

export function hasWorkbenchRole(operator?: CurrentOperatorView | null): boolean {
  const roles = normalizedOperatorRoles(operator);
  return [...roles].some((role) => WORKSTATION_ROLES.has(role));
}

export function hasSupervisorRole(operator?: CurrentOperatorView | null): boolean {
  const roles = normalizedOperatorRoles(operator);
  return [...roles].some((role) => SUPERVISOR_ROLES.has(role));
}

function requireWorkbenchRole(operator?: CurrentOperatorView | null): PermissionCheck {
  if (!operator?.operatorId) {
    return deny("未获取到当前坐席身份");
  }
  if (!hasWorkbenchRole(operator)) {
    return deny("当前账号没有坐席操作权限");
  }
  return allow();
}

function isOwnedByCurrentOperator(
  assignedOperator: string | null | undefined,
  operator?: CurrentOperatorView | null,
): boolean {
  if (!assignedOperator) {
    return true;
  }
  return assignedOperator === operator?.operatorId || hasSupervisorRole(operator);
}

function requireTicketOwnership(
  assignedOperator: string | null | undefined,
  operator?: CurrentOperatorView | null,
): PermissionCheck {
  if (isOwnedByCurrentOperator(assignedOperator, operator)) {
    return allow();
  }
  return deny(`工单已由 ${assignedOperator} 处理，当前坐席不能操作`);
}

export function canClaimTicket(
  operator: CurrentOperatorView | null | undefined,
  ticket: TicketSummary,
): PermissionCheck {
  const roleCheck = requireWorkbenchRole(operator);
  if (!roleCheck.allowed) return roleCheck;
  if (ticket.status !== "PENDING") {
    return deny("当前工单状态不允许领取");
  }
  return requireTicketOwnership(ticket.assignedAgent, operator);
}

export function canAddInternalNote(
  operator: CurrentOperatorView | null | undefined,
): PermissionCheck {
  return requireWorkbenchRole(operator);
}

export function canReviewTicket(
  operator: CurrentOperatorView | null | undefined,
  detail: TicketDetail,
): PermissionCheck {
  const roleCheck = requireWorkbenchRole(operator);
  if (!roleCheck.allowed) return roleCheck;
  if (!detail.approval) {
    return deny("当前工单没有审批任务");
  }
  if (TERMINAL_TICKET_STATUSES.has(detail.ticket.status)) {
    return deny("当前工单已结束，不能继续审批");
  }
  if (!REVIEWABLE_APPROVAL_STATUSES.has(detail.approval.status)) {
    return deny("当前审批状态不允许操作");
  }
  return requireTicketOwnership(detail.ticket.assignedAgent, operator);
}

export function canStartTakeover(
  operator: CurrentOperatorView | null | undefined,
  detail: TicketDetail,
): PermissionCheck {
  const roleCheck = requireWorkbenchRole(operator);
  if (!roleCheck.allowed) return roleCheck;
  if (TERMINAL_TICKET_STATUSES.has(detail.ticket.status)) {
    return deny("当前工单已结束，不能开始接管");
  }
  if (detail.takeover && !STARTABLE_TAKEOVER_STATUSES.has(detail.takeover.status)) {
    return deny("当前接管状态不允许开始接管");
  }
  const assignedOperator = detail.takeover?.assignedAgent || detail.ticket.assignedAgent;
  return requireTicketOwnership(assignedOperator, operator);
}

export function canFinishTakeover(
  operator: CurrentOperatorView | null | undefined,
  detail: TicketDetail,
): PermissionCheck {
  const roleCheck = requireWorkbenchRole(operator);
  if (!roleCheck.allowed) return roleCheck;
  if (TERMINAL_TICKET_STATUSES.has(detail.ticket.status)) {
    return deny("当前工单已结束，不能结束接管");
  }
  if (detail.takeover?.status !== "IN_PROGRESS") {
    return deny("人工接管未开始，不能结束接管");
  }
  const assignedOperator = detail.takeover.assignedAgent || detail.ticket.assignedAgent;
  return requireTicketOwnership(assignedOperator, operator);
}

export function canSendTakeoverMessage(
  operator: CurrentOperatorView | null | undefined,
  detail: TicketDetail,
): PermissionCheck {
  const roleCheck = requireWorkbenchRole(operator);
  if (!roleCheck.allowed) return roleCheck;
  if (detail.takeover?.status !== "IN_PROGRESS") {
    return deny("开始人工接管后才能发送人工消息");
  }
  const assignedOperator = detail.takeover.assignedAgent || detail.ticket.assignedAgent;
  return requireTicketOwnership(assignedOperator, operator);
}
