import React, { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Tag, Space, message, Row, Col, Card, Statistic, Tooltip } from "antd";
import { EyeOutlined } from "@ant-design/icons";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { ProTable } from "@ant-design/pro-components";
import dayjs from "dayjs";
import {
  fetchTickets,
  fetchTicketStats,
  claimTicket,
  fetchCurrentOperator,
} from "../../services/api";
import type {
  CurrentOperatorView,
  TicketSummary,
  TicketStatsView,
  WorkOrderStatus,
  RouteDecision,
  RiskLevel,
  Priority,
} from "../../types/workbench";
import {
  WORK_ORDER_STATUS_MAP,
  RISK_LEVEL_MAP,
  PRIORITY_MAP,
  ROUTE_DECISION_MAP,
} from "../../constants/workbench";
import { canClaimTicket } from "../../utils/permissions";

const statusOptions: { label: string; value: WorkOrderStatus }[] = [
  { label: "待处理", value: "PENDING" },
  { label: "已分配", value: "ASSIGNED" },
  { label: "处理中", value: "PROCESSING" },
  { label: "已通过", value: "APPROVED" },
  { label: "已驳回", value: "REJECTED" },
  { label: "已解决", value: "RESOLVED" },
  { label: "已关闭", value: "CLOSED" },
  { label: "已升级", value: "ESCALATED" },
];

const routeOptions: { label: string; value: RouteDecision }[] = [
  { label: "自动回复", value: "AUTO_REPLY" },
  { label: "自动执行", value: "AUTO_EXECUTE" },
  { label: "确认后执行", value: "CONFIRM_BEFORE_EXECUTE" },
  { label: "人工审核", value: "HUMAN_REVIEW" },
  { label: "人工接管", value: "HUMAN_TAKEOVER" },
  { label: "拒绝", value: "REJECT" },
];

const riskLevelOptions: { label: string; value: RiskLevel }[] = [
  { label: "L0 低风险", value: "L0" },
  { label: "L1 自动执行", value: "L1" },
  { label: "L2 需确认", value: "L2" },
  { label: "L3 人工兜底", value: "L3" },
];

const priorityOptions: { label: string; value: Priority }[] = [
  { label: "低", value: "LOW" },
  { label: "普通", value: "NORMAL" },
  { label: "高", value: "HIGH" },
  { label: "紧急", value: "URGENT" },
];

const intentOptions = [
  { label: "FAQ 问答", value: "faq.query" },
  { label: "查询订单", value: "order.query" },
  { label: "查询物流", value: "logistics.query" },
  { label: "修改地址", value: "order.modify_address" },
  { label: "取消订单", value: "order.cancel" },
  { label: "退款申请", value: "refund.apply" },
  { label: "换货申请", value: "exchange.apply" },
  { label: "未知意图", value: "unknown" },
];

const TicketList: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const navigate = useNavigate();
  const [stats, setStats] = useState<TicketStatsView | null>(null);
  const [statsLoading, setStatsLoading] = useState(false);
  const [currentOperator, setCurrentOperator] = useState<CurrentOperatorView | null>(null);
  const [operatorLoading, setOperatorLoading] = useState(true);

  const loadStats = useCallback(async () => {
    setStatsLoading(true);
    try {
      setStats(await fetchTicketStats());
    } catch (err) {
      message.error((err as Error).message || "统计数据加载失败");
    } finally {
      setStatsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadStats();
  }, [loadStats]);

  useEffect(() => {
    let mounted = true;
    setOperatorLoading(true);
    fetchCurrentOperator()
      .then((operator) => {
        if (mounted) {
          setCurrentOperator(operator);
        }
      })
      .catch((err) => {
        if (mounted) {
          setCurrentOperator(null);
          message.error((err as Error).message || "当前坐席身份加载失败");
        }
      })
      .finally(() => {
        if (mounted) {
          setOperatorLoading(false);
        }
      });
    return () => {
      mounted = false;
    };
  }, []);

  const handleClaim = async (record: TicketSummary) => {
    const permission = canClaimTicket(currentOperator, record);
    if (operatorLoading || !permission.allowed) {
      message.warning(
        operatorLoading ? "正在加载坐席身份" : permission.reason || "当前账号没有操作权限",
      );
      return;
    }
    try {
      await claimTicket(record.ticketId, {
        comment: "领取工单",
      });
      message.success("领取成功");
      await loadStats();
      actionRef.current?.reload();
    } catch (err) {
      message.error((err as Error).message || "领取失败");
    }
  };

  const columns: ProColumns<TicketSummary>[] = [
    {
      title: "工单ID",
      dataIndex: "ticketId",
      ellipsis: true,
      width: 180,
      copyable: true,
      search: false,
    },
    {
      title: "用户ID",
      dataIndex: "userId",
      width: 120,
      search: false,
    },
    {
      title: "意图",
      dataIndex: "intent",
      ellipsis: true,
      width: 140,
      valueType: "select",
      fieldProps: { options: intentOptions, showSearch: true },
    },
    {
      title: "风险等级",
      dataIndex: "riskLevel",
      width: 90,
      valueType: "select",
      fieldProps: { options: riskLevelOptions },
      render: (_, record) => {
        const cfg = RISK_LEVEL_MAP[record.riskLevel];
        return <Tag color={cfg?.color}>{cfg?.text || record.riskLevel}</Tag>;
      },
    },
    {
      title: "路由决策",
      dataIndex: "routeDecision",
      width: 120,
      valueType: "select",
      fieldProps: { options: routeOptions },
      render: (_, record) =>
        ROUTE_DECISION_MAP[record.routeDecision] || record.routeDecision,
    },
    {
      title: "状态",
      dataIndex: "status",
      width: 100,
      valueType: "select",
      fieldProps: { options: statusOptions },
      render: (_, record) => {
        const cfg = WORK_ORDER_STATUS_MAP[record.status];
        return <Tag color={cfg?.color}>{cfg?.text || record.status}</Tag>;
      },
    },
    {
      title: "优先级",
      dataIndex: "priority",
      width: 80,
      valueType: "select",
      fieldProps: { options: priorityOptions },
      render: (_, record) => {
        const cfg = PRIORITY_MAP[record.priority];
        return <Tag color={cfg?.color}>{cfg?.text || record.priority}</Tag>;
      },
    },
    {
      title: "坐席",
      dataIndex: "assignedAgent",
      width: 100,
      fieldProps: { placeholder: "输入坐席 ID" },
      render: (_, record) => record.assignedAgent || "-",
    },
    {
      title: "关键词",
      dataIndex: "keyword",
      hideInTable: true,
      fieldProps: { placeholder: "搜索 ticketId / userId / 意图" },
    },
    {
      title: "创建时间",
      dataIndex: "createdAt",
      width: 170,
      search: false,
      render: (_, record) =>
        record.createdAt ? dayjs(record.createdAt).format("YYYY-MM-DD HH:mm:ss") : "-",
    },
    {
      title: "创建时间",
      dataIndex: "createdAtRange",
      valueType: "dateTimeRange",
      hideInTable: true,
      search: {
        transform: (value) => ({
          createdAtFrom: value?.[0],
          createdAtTo: value?.[1],
        }),
      },
      fieldProps: { placeholder: ["开始时间", "结束时间"] },
    },
    {
      title: "操作",
      valueType: "option",
      width: 150,
      fixed: "right",
      render: (_, record) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => navigate(`/tickets/${record.ticketId}`)}
          >
            详情
          </Button>
          {record.status === "PENDING" && (
            <Tooltip
              title={
                operatorLoading
                  ? "正在加载坐席身份"
                  : canClaimTicket(currentOperator, record).reason
              }
            >
              <span>
                <Button
                  type="link"
                  size="small"
                  disabled={operatorLoading || !canClaimTicket(currentOperator, record).allowed}
                  onClick={() => handleClaim(record)}
                >
                  领取
                </Button>
              </span>
            </Tooltip>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <Row gutter={16}>
        <Col xs={24} sm={12} lg={8} xl={4}>
          <Card size="small" loading={statsLoading}>
            <Statistic title="总工单" value={stats?.total || 0} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={8} xl={4}>
          <Card size="small" loading={statsLoading}>
            <Statistic title="待处理" value={stats?.pending || 0} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={8} xl={4}>
          <Card size="small" loading={statsLoading}>
            <Statistic title="处理中" value={stats?.processing || 0} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={8} xl={4}>
          <Card size="small" loading={statsLoading}>
            <Statistic title="已完成" value={stats?.completed || 0} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={8} xl={4}>
          <Card size="small" loading={statsLoading}>
            <Statistic
              title="超时风险"
              value={stats?.overdueRisk || 0}
              valueStyle={{ color: stats?.overdueRisk ? "#cf1322" : undefined }}
            />
          </Card>
        </Col>
      </Row>

      <ProTable<TicketSummary>
        headerTitle="工单列表"
        actionRef={actionRef}
        rowKey="ticketId"
        columns={columns}
        request={async (params) => {
          try {
            const { current, pageSize, ...rest } = params;
            const result = await fetchTickets({
              pageNo: current,
              pageSize,
              status: rest.status as WorkOrderStatus | undefined,
              routeDecision: rest.routeDecision as RouteDecision | undefined,
              riskLevel: rest.riskLevel as RiskLevel | undefined,
              intent: rest.intent as string | undefined,
              priority: rest.priority as Priority | undefined,
              assignedAgent: rest.assignedAgent as string | undefined,
              keyword: rest.keyword as string | undefined,
              createdAtFrom: rest.createdAtFrom as string | undefined,
              createdAtTo: rest.createdAtTo as string | undefined,
            });
            return {
              data: result.records,
              total: result.total,
              success: true,
            };
          } catch (err) {
            message.error((err as Error).message || "查询失败");
            return { data: [], total: 0, success: false };
          }
        }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        search={{ labelWidth: "auto", collapsed: false }}
        scroll={{ x: 1200 }}
        dateFormatter="string"
      />
    </Space>
  );
};

export default TicketList;
