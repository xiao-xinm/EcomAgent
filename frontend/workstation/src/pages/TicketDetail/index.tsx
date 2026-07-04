import React, { useEffect, useState, useCallback } from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
  Button,
  Card,
  Col,
  Collapse,
  Descriptions,
  Empty,
  Input,
  message,
  Modal,
  Row,
  Space,
  Spin,
  Tag,
  Timeline,
  Typography,
} from "antd";
import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  LoginOutlined,
  LogoutOutlined,
  MessageOutlined,
  SendOutlined,
  TeamOutlined,
} from "@ant-design/icons";
import dayjs from "dayjs";
import {
  fetchTicketDetail,
  claimTicket,
  approveTicket,
  rejectTicket,
  startTakeover,
  finishTakeover,
  addInternalNote,
  sendTakeoverMessage,
} from "../../services/api";
import type {
  ActionLogView,
  ApprovalTaskView,
  TicketDetail as TicketDetailType,
} from "../../types/workbench";
import {
  WORK_ORDER_STATUS_MAP,
  APPROVAL_STATUS_MAP,
  TAKEOVER_STATUS_MAP,
  RISK_LEVEL_MAP,
  PRIORITY_MAP,
  ROUTE_DECISION_MAP,
  DEFAULT_OPERATOR_ID,
} from "../../constants/workbench";

const { Title, Text } = Typography;

const ACTION_TYPE_META: Record<string, { text: string; color: string }> = {
  CREATE: { text: "创建工单", color: "blue" },
  ASSIGN: { text: "领取工单", color: "processing" },
  CLAIM: { text: "领取审批", color: "processing" },
  APPROVE: { text: "审批通过", color: "success" },
  REJECT: { text: "审批驳回", color: "error" },
  MODIFY_AND_APPROVE: { text: "修改并通过", color: "success" },
  TAKEOVER: { text: "人工接管", color: "cyan" },
  ESCALATE: { text: "升级处理", color: "warning" },
  CLOSE: { text: "关闭工单", color: "default" },
  CANCEL: { text: "取消", color: "default" },
  EXPIRE: { text: "超时", color: "warning" },
  COMMENT: { text: "审批备注", color: "gold" },
  INTERNAL_NOTE: { text: "内部备注", color: "gold" },
};

const ACTION_SOURCE_META: Record<string, { text: string; color: string }> = {
  WORK_ORDER: { text: "工单", color: "blue" },
  APPROVAL: { text: "审批", color: "purple" },
};

function readStringValue(record: Record<string, unknown>, key: string) {
  const value = record[key];
  return typeof value === "string" ? value : undefined;
}

function actionDisplay(action: ActionLogView) {
  const subAction = readStringValue(action.actionData, "subAction");
  if (action.actionType === "TAKEOVER" && subAction === "MESSAGE_SENT") {
    return { text: "发送人工消息", color: "cyan" };
  }
  return ACTION_TYPE_META[action.actionType] || {
    text: action.actionType,
    color: "default",
  };
}

function actionTimelineColor(action: ActionLogView) {
  if (action.afterStatus === "APPROVED" || action.afterStatus === "RESOLVED") {
    return "green";
  }
  if (action.afterStatus === "REJECTED" || action.afterStatus === "CANCELLED") {
    return "red";
  }
  if (action.actionType === "INTERNAL_NOTE") {
    return "gold";
  }
  if (action.actionType === "TAKEOVER") {
    return "cyan";
  }
  return action.source === "APPROVAL" ? "purple" : "blue";
}

function actionDataJson(action: ActionLogView) {
  if (!action.actionData || Object.keys(action.actionData).length === 0) {
    return null;
  }
  return JSON.stringify(action.actionData, null, 2);
}

function payloadText(payload: Record<string, unknown>, key: string) {
  const value = payload[key];
  if (typeof value === "string") {
    return value.trim();
  }
  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  return "";
}

function textOrDash(value: string) {
  return value || "-";
}

function refundAmountText(payload: Record<string, unknown>) {
  const value = payload.refundAmount;
  if (typeof value === "number") {
    return `${value} 元`;
  }
  if (typeof value === "string" && value.trim()) {
    return `${value.trim()} 元`;
  }
  return "-";
}

function evidencePlaceholderText(payload: Record<string, unknown>) {
  const value = payload.evidencePlaceholders;
  if (!Array.isArray(value) || value.length === 0) {
    return "-";
  }

  const items = value
    .map((item) => {
      if (!item || typeof item !== "object") {
        return String(item || "");
      }
      const record = item as Record<string, unknown>;
      const label = payloadText(record, "label") || "凭证";
      const required = record.required === true ? "必填" : "选填";
      const status = payloadText(record, "status") || "UNKNOWN";
      return `${label}（${required}，${status}）`;
    })
    .filter(Boolean);

  return items.length > 0 ? items.join("；") : "-";
}

function isRefundApproval(
  approval?: ApprovalTaskView | null,
): approval is ApprovalTaskView {
  return approval?.approvalType === "REFUND";
}

function isExchangeApproval(
  approval?: ApprovalTaskView | null,
): approval is ApprovalTaskView {
  return approval?.approvalType === "EXCHANGE";
}

const TicketDetailPage: React.FC = () => {
  const { ticketId } = useParams<{ ticketId: string }>();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [detail, setDetail] = useState<TicketDetailType | null>(null);
  const [commentModal, setCommentModal] = useState<{
    open: boolean;
    action: string;
    onConfirm: (comment: string) => Promise<void> | void;
  }>({ open: false, action: "", onConfirm: () => {} });
  const [commentValue, setCommentValue] = useState("");
  const [actionLoading, setActionLoading] = useState(false);
  const [internalNote, setInternalNote] = useState("");
  const [noteLoading, setNoteLoading] = useState(false);
  const [takeoverMessage, setTakeoverMessage] = useState("");
  const [messageSending, setMessageSending] = useState(false);

  const loadDetail = useCallback(async () => {
    if (!ticketId) return;
    setLoading(true);
    try {
      const data = await fetchTicketDetail(ticketId);
      setDetail(data);
    } catch (err) {
      message.error((err as Error).message || "加载详情失败");
    } finally {
      setLoading(false);
    }
  }, [ticketId]);

  useEffect(() => {
    loadDetail();
  }, [loadDetail]);

  const withComment = (action: string, fn: (comment: string) => Promise<void>) => {
    setCommentValue("");
    setCommentModal({ open: true, action, onConfirm: fn });
  };

  const handleClaim = () =>
    withComment("领取工单", async (comment) => {
      await claimTicket(ticketId!, {
        operatorId: DEFAULT_OPERATOR_ID,
        comment: comment || "领取工单",
      });
      message.success("领取成功");
      await loadDetail();
    });

  const handleApprove = () =>
    withComment("审批通过", async (comment) => {
      await approveTicket(ticketId!, {
        operatorId: DEFAULT_OPERATOR_ID,
        comment: comment || "审批通过",
      });
      message.success("审批通过");
      await loadDetail();
    });

  const handleReject = () =>
    withComment("审批驳回", async (comment) => {
      await rejectTicket(ticketId!, {
        operatorId: DEFAULT_OPERATOR_ID,
        comment: comment || "审批驳回",
      });
      message.success("已驳回");
      await loadDetail();
    });

  const handleStartTakeover = () =>
    withComment("开始接管", async (comment) => {
      await startTakeover(ticketId!, {
        operatorId: DEFAULT_OPERATOR_ID,
        comment: comment || "开始人工接管",
      });
      message.success("已开始接管");
      await loadDetail();
    });

  const handleFinishTakeover = () =>
    withComment("结束接管", async (comment) => {
      await finishTakeover(ticketId!, {
        operatorId: DEFAULT_OPERATOR_ID,
        comment: comment || "结束人工接管",
        resolutionStatus: "RESOLVED",
      });
      message.success("已结束接管");
      await loadDetail();
    });

  const handleAddInternalNote = async () => {
    const comment = internalNote.trim();
    if (!comment) {
      message.warning("请输入内部备注");
      return;
    }
    setNoteLoading(true);
    try {
      await addInternalNote(ticketId!, {
        operatorId: DEFAULT_OPERATOR_ID,
        comment,
        payload: { source: "ticket-detail" },
      });
      message.success("内部备注已记录");
      setInternalNote("");
      await loadDetail();
    } catch (err) {
      message.error((err as Error).message || "内部备注提交失败");
    } finally {
      setNoteLoading(false);
    }
  };

  const handleSendTakeoverMessage = async () => {
    const content = takeoverMessage.trim();
    if (!content) {
      message.warning("请输入要发送给用户的消息");
      return;
    }
    setMessageSending(true);
    try {
      await sendTakeoverMessage(ticketId!, {
        operatorId: DEFAULT_OPERATOR_ID,
        content,
        payload: { source: "ticket-detail" },
      });
      message.success("人工消息已发送");
      setTakeoverMessage("");
      await loadDetail();
    } catch (err) {
      message.error((err as Error).message || "人工消息发送失败");
    } finally {
      setMessageSending(false);
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: "center", padding: 80 }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!detail) {
    return (
      <div style={{ padding: 24 }}>
        <Text type="danger">工单不存在或加载失败</Text>
      </div>
    );
  }

  const { ticket, approval, takeover, messages, actions } = detail;
  const internalNotes = actions.filter(
    (action) => action.source === "WORK_ORDER" && action.actionType === "INTERNAL_NOTE",
  );
  const woStatus = WORK_ORDER_STATUS_MAP[ticket.status];
  const riskCfg = RISK_LEVEL_MAP[ticket.riskLevel];
  const prioCfg = PRIORITY_MAP[ticket.priority];
  const isTerminalTicket = ["APPROVED", "REJECTED", "RESOLVED", "CLOSED"].includes(
    ticket.status,
  );

  const canClaim = ticket.status === "PENDING";
  const canApprove =
    approval &&
    !isTerminalTicket &&
    (approval.status === "PENDING" || approval.status === "CLAIMED");
  const canStartTakeover =
    !isTerminalTicket &&
    (!takeover || ["REQUESTED", "QUEUED", "ASSIGNED"].includes(takeover.status));
  const canFinishTakeover =
    !isTerminalTicket && takeover && takeover.status === "IN_PROGRESS";
  const canSendTakeoverMessage = takeover?.status === "IN_PROGRESS";

  return (
    <div style={{ padding: 16 }}>
      {/* Header */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          marginBottom: 16,
        }}
      >
        <Space>
          <Button
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate("/tickets")}
          >
            返回
          </Button>
          <Title level={5} style={{ margin: 0 }}>
            工单详情
          </Title>
        </Space>
        <Space>
          {canClaim && (
            <Button type="primary" icon={<TeamOutlined />} onClick={handleClaim}>
              领取工单
            </Button>
          )}
          {canApprove && (
            <>
              <Button
                type="primary"
                icon={<CheckCircleOutlined />}
                onClick={handleApprove}
              >
                审批通过
              </Button>
              <Button
                danger
                icon={<CloseCircleOutlined />}
                onClick={handleReject}
              >
                审批驳回
              </Button>
            </>
          )}
          {canStartTakeover && (
            <Button icon={<LoginOutlined />} onClick={handleStartTakeover}>
              开始接管
            </Button>
          )}
          {canFinishTakeover && (
            <Button icon={<LogoutOutlined />} onClick={handleFinishTakeover}>
              结束接管
            </Button>
          )}
        </Space>
      </div>

      <Row gutter={16}>
        {/* Left column: ticket info + messages */}
        <Col span={16}>
          <Card title="工单信息" size="small" style={{ marginBottom: 16 }}>
            <Descriptions column={3} size="small" bordered>
              <Descriptions.Item label="工单ID">
                <Text copyable>{ticket.ticketId}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="用户ID">{ticket.userId}</Descriptions.Item>
              <Descriptions.Item label="会话ID">
                <Text copyable>{ticket.sessionId}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="意图">{ticket.intent}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={woStatus?.color}>{woStatus?.text || ticket.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="风险等级">
                <Tag color={riskCfg?.color}>{riskCfg?.text}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="路由决策">
                {ROUTE_DECISION_MAP[ticket.routeDecision] || ticket.routeDecision}
              </Descriptions.Item>
              <Descriptions.Item label="优先级">
                <Tag color={prioCfg?.color}>{prioCfg?.text}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="坐席">
                {ticket.assignedAgent || "-"}
              </Descriptions.Item>
              <Descriptions.Item label="原因" span={3}>
                {ticket.reason || "-"}
              </Descriptions.Item>
              <Descriptions.Item label="创建时间">
                {ticket.createdAt
                  ? dayjs(ticket.createdAt).format("YYYY-MM-DD HH:mm:ss")
                  : "-"}
              </Descriptions.Item>
              <Descriptions.Item label="更新时间">
                {ticket.updatedAt
                  ? dayjs(ticket.updatedAt).format("YYYY-MM-DD HH:mm:ss")
                  : "-"}
              </Descriptions.Item>
              <Descriptions.Item label="SLA截止">
                {ticket.slaDeadline
                  ? dayjs(ticket.slaDeadline).format("YYYY-MM-DD HH:mm:ss")
                  : "-"}
              </Descriptions.Item>
            </Descriptions>
          </Card>

          {/* Messages */}
          <Card
            title={`会话消息 (${messages.length})`}
            size="small"
            style={{ marginBottom: 16 }}
          >
            {messages.length === 0 ? (
              <Text type="secondary">暂无消息</Text>
            ) : (
              <div style={{ maxHeight: 400, overflow: "auto" }}>
                {messages.map((msg) => {
                  const isUser = msg.role === "USER";
                  const isSystem = msg.role === "SYSTEM";
                  return (
                    <div
                      key={msg.messageId}
                      style={{
                        display: "flex",
                        justifyContent: isUser
                          ? "flex-start"
                          : isSystem
                            ? "center"
                            : "flex-end",
                        marginBottom: 8,
                      }}
                    >
                      <div
                        style={{
                          maxWidth: "70%",
                          padding: "8px 12px",
                          borderRadius: 8,
                          background: isUser
                            ? "#f0f0f0"
                            : isSystem
                              ? "#fffbe6"
                              : "#e6f4ff",
                          border: isSystem ? "1px solid #ffe58f" : "none",
                        }}
                      >
                        <div style={{ marginBottom: 4 }}>
                          <Text strong style={{ fontSize: 12 }}>
                            {isUser
                              ? "用户"
                              : msg.role === "HUMAN_AGENT"
                                ? "人工坐席"
                                : msg.role === "AGENT"
                                  ? "AI Agent"
                                  : "系统"}
                          </Text>
                          <Text
                            type="secondary"
                            style={{ fontSize: 11, marginLeft: 8 }}
                          >
                            {msg.createdAt
                              ? dayjs(msg.createdAt).format("HH:mm:ss")
                              : ""}
                          </Text>
                        </div>
                        <div style={{ whiteSpace: "pre-wrap", fontSize: 13 }}>
                          {msg.content || `(${msg.messageType})`}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </Card>
        </Col>

        {/* Right column: approval, takeover, action log */}
        <Col span={8}>
          {/* Approval info */}
          {approval && (
            <Card
              title="审批信息"
              size="small"
              style={{ marginBottom: 16 }}
            >
              <Descriptions column={1} size="small" bordered>
                <Descriptions.Item label="审批ID">
                  {approval.approvalId}
                </Descriptions.Item>
                <Descriptions.Item label="审批类型">
                  {approval.approvalType}
                </Descriptions.Item>
                <Descriptions.Item label="审批状态">
                  <Tag
                    color={
                      APPROVAL_STATUS_MAP[approval.status]?.color
                    }
                  >
                    {APPROVAL_STATUS_MAP[approval.status]?.text ||
                      approval.status}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="风险等级">
                  {approval.riskLevel}
                </Descriptions.Item>
                <Descriptions.Item label="风险原因">
                  {approval.riskReason || "-"}
                </Descriptions.Item>
                <Descriptions.Item label="审核人">
                  {approval.assignedReviewer || "-"}
                </Descriptions.Item>
                <Descriptions.Item label="创建时间">
                  {approval.createdAt
                    ? dayjs(approval.createdAt).format("YYYY-MM-DD HH:mm:ss")
                    : "-"}
                </Descriptions.Item>
              </Descriptions>
            </Card>
          )}

          {/* 退款审核上下文只展示工单字段，不代表已经执行真实退款。 */}
          {isRefundApproval(approval) && (
            <Card
              title="退款申请"
              size="small"
              style={{ marginBottom: 16 }}
            >
              <Descriptions column={1} size="small" bordered>
                <Descriptions.Item label="订单号">
                  {textOrDash(payloadText(approval.requestPayload, "orderNo"))}
                </Descriptions.Item>
                <Descriptions.Item label="退款原因">
                  {textOrDash(payloadText(approval.requestPayload, "refundReason"))}
                </Descriptions.Item>
                <Descriptions.Item label="退款金额">
                  {refundAmountText(approval.requestPayload)}
                </Descriptions.Item>
                <Descriptions.Item label="凭证占位">
                  {evidencePlaceholderText(approval.requestPayload)}
                </Descriptions.Item>
                <Descriptions.Item label="用户诉求">
                  {textOrDash(
                    payloadText(approval.requestPayload, "userRequest") ||
                      payloadText(approval.requestPayload, "content"),
                  )}
                </Descriptions.Item>
              </Descriptions>
            </Card>
          )}

          {/* 换货审核上下文只展示工单字段，不代表已经执行真实换货。 */}
          {isExchangeApproval(approval) && (
            <Card
              title="换货申请"
              size="small"
              style={{ marginBottom: 16 }}
            >
              <Descriptions column={1} size="small" bordered>
                <Descriptions.Item label="订单号">
                  {textOrDash(payloadText(approval.requestPayload, "orderNo"))}
                </Descriptions.Item>
                <Descriptions.Item label="商品">
                  {textOrDash(payloadText(approval.requestPayload, "productName"))}
                </Descriptions.Item>
                <Descriptions.Item label="换货原因">
                  {textOrDash(payloadText(approval.requestPayload, "exchangeReason"))}
                </Descriptions.Item>
                <Descriptions.Item label="期望处理">
                  {textOrDash(payloadText(approval.requestPayload, "expectedHandling"))}
                </Descriptions.Item>
                <Descriptions.Item label="凭证占位">
                  {evidencePlaceholderText(approval.requestPayload)}
                </Descriptions.Item>
                <Descriptions.Item label="用户诉求">
                  {textOrDash(
                    payloadText(approval.requestPayload, "userRequest") ||
                      payloadText(approval.requestPayload, "content"),
                  )}
                </Descriptions.Item>
              </Descriptions>
            </Card>
          )}

          {/* Takeover info */}
          {takeover && (
            <Card
              title="人工接管"
              size="small"
              style={{ marginBottom: 16 }}
            >
              <Descriptions column={1} size="small" bordered>
                <Descriptions.Item label="接管ID">
                  {takeover.takeoverId}
                </Descriptions.Item>
                <Descriptions.Item label="接管状态">
                  <Tag
                    color={
                      TAKEOVER_STATUS_MAP[takeover.status]?.color
                    }
                  >
                    {TAKEOVER_STATUS_MAP[takeover.status]?.text ||
                      takeover.status}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="触发来源">
                  {takeover.triggerSource}
                </Descriptions.Item>
                <Descriptions.Item label="坐席">
                  {takeover.assignedAgent || "-"}
                </Descriptions.Item>
                <Descriptions.Item label="原因">
                  {takeover.reason || "-"}
                </Descriptions.Item>
                <Descriptions.Item label="开始时间">
                  {takeover.startedAt
                    ? dayjs(takeover.startedAt).format("YYYY-MM-DD HH:mm:ss")
                    : "-"}
                </Descriptions.Item>
              </Descriptions>
            </Card>
          )}

          <Card title="人工消息" size="small" style={{ marginBottom: 16 }}>
            <Space direction="vertical" style={{ width: "100%" }} size={12}>
              <Input.TextArea
                rows={3}
                maxLength={500}
                showCount
                disabled={!canSendTakeoverMessage}
                placeholder={
                  canSendTakeoverMessage
                    ? "输入要发送给用户的人工客服消息"
                    : "开始人工接管后可发送消息"
                }
                value={takeoverMessage}
                onChange={(e) => setTakeoverMessage(e.target.value)}
              />
              <Button
                type="primary"
                icon={<SendOutlined />}
                loading={messageSending}
                disabled={!canSendTakeoverMessage}
                onClick={handleSendTakeoverMessage}
                block
              >
                发送给用户
              </Button>
              <Text type="secondary" style={{ fontSize: 12 }}>
                消息会写入当前会话，用户端 H5 通过轮询看到。
              </Text>
            </Space>
          </Card>

          <Card title="内部协作" size="small" style={{ marginBottom: 16 }}>
            <Space direction="vertical" style={{ width: "100%" }} size={12}>
              <Input.TextArea
                rows={3}
                maxLength={500}
                showCount
                placeholder="记录仅坐席可见的处理备注、协作信息或后续跟进点"
                value={internalNote}
                onChange={(e) => setInternalNote(e.target.value)}
              />
              <Button
                type="primary"
                icon={<MessageOutlined />}
                loading={noteLoading}
                onClick={handleAddInternalNote}
                block
              >
                添加内部备注
              </Button>
              {internalNotes.length === 0 ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无内部备注" />
              ) : (
                <Timeline
                  items={internalNotes.map((note) => ({
                    color: "gold",
                    children: (
                      <div>
                        <div style={{ marginBottom: 2 }}>
                          <Text strong>{note.operatorId}</Text>
                          <Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>
                            {note.createdAt
                              ? dayjs(note.createdAt).format("MM-DD HH:mm:ss")
                              : ""}
                          </Text>
                        </div>
                        <div style={{ fontSize: 12, whiteSpace: "pre-wrap" }}>
                          {note.comment}
                        </div>
                      </div>
                    ),
                  }))}
                />
              )}
            </Space>
          </Card>

          {/* Action log timeline */}
          <Card
            title={`审计时间线 (${actions.length})`}
            size="small"
            extra={<Text type="secondary">按时间升序</Text>}
          >
            {actions.length === 0 ? (
              <Text type="secondary">暂无操作记录</Text>
            ) : (
              <Timeline
                items={actions.map((action) => {
                  const actionMeta = actionDisplay(action);
                  const sourceMeta = ACTION_SOURCE_META[action.source] || {
                    text: action.source,
                    color: "default",
                  };
                  const dataJson = actionDataJson(action);
                  return {
                    color: actionTimelineColor(action),
                    children: (
                      <div>
                        <Space wrap size={[4, 4]} style={{ marginBottom: 4 }}>
                          <Tag color={sourceMeta.color}>{sourceMeta.text}</Tag>
                          <Tag color={actionMeta.color}>{actionMeta.text}</Tag>
                          {action.beforeStatus && action.afterStatus && (
                            <Tag>
                              {action.beforeStatus} → {action.afterStatus}
                            </Tag>
                          )}
                        </Space>
                        <div>
                          <Text strong>{action.operatorId}</Text>
                          <Text
                            type="secondary"
                            style={{ fontSize: 12, marginLeft: 8 }}
                          >
                            {action.createdAt
                              ? dayjs(action.createdAt).format("YYYY-MM-DD HH:mm:ss")
                              : ""}
                          </Text>
                        </div>
                        {action.comment && (
                          <div
                            style={{
                              marginTop: 4,
                              fontSize: 12,
                              whiteSpace: "pre-wrap",
                            }}
                          >
                            {action.comment}
                          </div>
                        )}
                        {dataJson && (
                          <Collapse
                            ghost
                            size="small"
                            style={{ marginTop: 4 }}
                            items={[
                              {
                                key: `${action.actionId}-data`,
                                label: "查看动作数据",
                                children: (
                                  <pre
                                    style={{
                                      margin: 0,
                                      whiteSpace: "pre-wrap",
                                      wordBreak: "break-word",
                                      fontSize: 12,
                                    }}
                                  >
                                    {dataJson}
                                  </pre>
                                ),
                              },
                            ]}
                          />
                        )}
                      </div>
                    ),
                  };
                })}
              />
            )}
          </Card>
        </Col>
      </Row>

      {/* Comment modal */}
      <Modal
        title={commentModal.action}
        open={commentModal.open}
        confirmLoading={actionLoading}
        onCancel={() =>
          setCommentModal((prev) => ({ ...prev, open: false }))
        }
        onOk={async () => {
          setActionLoading(true);
          try {
            await commentModal.onConfirm(commentValue);
            setCommentModal((prev) => ({ ...prev, open: false }));
          } catch (err) {
            message.error((err as Error).message || "操作失败");
          } finally {
            setActionLoading(false);
          }
        }}
        okText="确认"
        cancelText="取消"
      >
        <Input.TextArea
          rows={3}
          placeholder="备注（可选）"
          value={commentValue}
          onChange={(e) => setCommentValue(e.target.value)}
        />
      </Modal>
    </div>
  );
};

export default TicketDetailPage;
