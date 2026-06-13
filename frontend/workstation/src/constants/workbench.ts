import type { WorkOrderStatus, ApprovalStatus, TakeoverStatus, RiskLevel, Priority, RouteDecision } from "../types/workbench";

export const WORK_ORDER_STATUS_MAP: Record<WorkOrderStatus, { text: string; color: string }> = {
  PENDING: { text: "待处理", color: "default" },
  ASSIGNED: { text: "已分配", color: "processing" },
  PROCESSING: { text: "处理中", color: "processing" },
  APPROVED: { text: "已通过", color: "success" },
  REJECTED: { text: "已驳回", color: "error" },
  RESOLVED: { text: "已解决", color: "success" },
  CLOSED: { text: "已关闭", color: "default" },
  ESCALATED: { text: "已升级", color: "warning" },
};

export const APPROVAL_STATUS_MAP: Record<ApprovalStatus, { text: string; color: string }> = {
  PENDING: { text: "待审批", color: "default" },
  CLAIMED: { text: "已领取", color: "processing" },
  APPROVED: { text: "已通过", color: "success" },
  REJECTED: { text: "已驳回", color: "error" },
  CANCELLED: { text: "已取消", color: "default" },
  EXPIRED: { text: "已过期", color: "default" },
  ESCALATED: { text: "已升级", color: "warning" },
};

export const TAKEOVER_STATUS_MAP: Record<TakeoverStatus, { text: string; color: string }> = {
  REQUESTED: { text: "已请求", color: "default" },
  QUEUED: { text: "排队中", color: "default" },
  ASSIGNED: { text: "已分配", color: "processing" },
  IN_PROGRESS: { text: "接管中", color: "processing" },
  RESOLVED: { text: "已解决", color: "success" },
  CANCELLED: { text: "已取消", color: "default" },
};

export const RISK_LEVEL_MAP: Record<RiskLevel, { text: string; color: string }> = {
  L0: { text: "L0", color: "green" },
  L1: { text: "L1", color: "blue" },
  L2: { text: "L2", color: "orange" },
  L3: { text: "L3", color: "red" },
};

export const PRIORITY_MAP: Record<Priority, { text: string; color: string }> = {
  LOW: { text: "低", color: "default" },
  NORMAL: { text: "普通", color: "blue" },
  HIGH: { text: "高", color: "orange" },
  URGENT: { text: "紧急", color: "red" },
};

export const ROUTE_DECISION_MAP: Record<RouteDecision, string> = {
  AUTO_REPLY: "自动回复",
  AUTO_EXECUTE: "自动执行",
  CONFIRM_BEFORE_EXECUTE: "确认后执行",
  HUMAN_REVIEW: "人工审核",
  HUMAN_TAKEOVER: "人工接管",
  REJECT: "拒绝",
};

export const DEFAULT_OPERATOR_ID = "agent_001";
