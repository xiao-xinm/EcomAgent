import React, { useCallback, useEffect, useRef, useState } from "react";
import { Tag, Typography, message } from "antd";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { ProTable } from "@ant-design/pro-components";
import dayjs from "dayjs";
import { fetchNotificationOutboxSummary } from "../../services/api";
import {
  fetchNotificationDeliverySummary,
  fetchNotificationEvents,
} from "../../services/notificationApi";
import type {
  NotificationDeliverySummary,
  NotificationEventQueryParams,
  NotificationEventStatus,
  NotificationEventView,
  NotificationOutboxSummary,
} from "../../types/notification";
import OperationsSummaryBar from "./OperationsSummaryBar";

const { Text } = Typography;

const NOTIFICATION_STATUS_META: Record<
  NotificationEventStatus,
  { text: string; color: string }
> = {
  ACCEPTED: { text: "已接收", color: "processing" },
  DELIVERED: { text: "已投递", color: "success" },
  FAILED: { text: "投递失败", color: "error" },
};

const notificationStatusOptions: { label: string; value: NotificationEventStatus }[] = [
  { label: "已接收", value: "ACCEPTED" },
  { label: "已投递", value: "DELIVERED" },
  { label: "投递失败", value: "FAILED" },
];

const formatTime = (value?: string | null) =>
  value ? dayjs(value).format("YYYY-MM-DD HH:mm:ss") : "-";

const NotificationEvents: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [outboxSummary, setOutboxSummary] = useState<NotificationOutboxSummary>();
  const [deliverySummary, setDeliverySummary] = useState<NotificationDeliverySummary>();
  const [summaryLoading, setSummaryLoading] = useState(false);
  const [summaryErrors, setSummaryErrors] = useState<string[]>([]);

  const loadSummaries = useCallback(async () => {
    setSummaryLoading(true);
    const [outboxResult, deliveryResult] = await Promise.allSettled([
      fetchNotificationOutboxSummary(),
      fetchNotificationDeliverySummary(),
    ]);
    const errors: string[] = [];

    if (outboxResult.status === "fulfilled") {
      setOutboxSummary(outboxResult.value);
    } else {
      errors.push(`Workbench Outbox：${(outboxResult.reason as Error).message || "查询失败"}`);
    }
    if (deliveryResult.status === "fulfilled") {
      setDeliverySummary(deliveryResult.value);
    } else {
      errors.push(`Notification Delivery：${(deliveryResult.reason as Error).message || "查询失败"}`);
    }

    setSummaryErrors(errors);
    setSummaryLoading(false);
  }, []);

  useEffect(() => {
    void loadSummaries();
  }, [loadSummaries]);

  const columns: ProColumns<NotificationEventView>[] = [
    {
      title: "事件 ID",
      dataIndex: "eventId",
      width: 210,
      copyable: true,
      ellipsis: true,
      search: false,
    },
    {
      title: "事件类型",
      dataIndex: "eventType",
      width: 230,
      ellipsis: true,
      fieldProps: { placeholder: "例如 APPROVAL_APPROVED" },
    },
    {
      title: "状态",
      dataIndex: "status",
      width: 110,
      valueType: "select",
      fieldProps: { options: notificationStatusOptions },
      render: (_, record) => {
        const meta = NOTIFICATION_STATUS_META[record.status];
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: "工单 ID",
      dataIndex: "ticketId",
      width: 190,
      copyable: true,
      ellipsis: true,
      fieldProps: { placeholder: "按工单 ID 查询" },
      render: (_, record) => record.ticketId || <Text type="secondary">-</Text>,
    },
    {
      title: "接收用户",
      dataIndex: "recipientUserId",
      width: 150,
      ellipsis: true,
      fieldProps: { placeholder: "按用户 ID 查询" },
      render: (_, record) => record.recipientUserId || <Text type="secondary">-</Text>,
    },
    {
      title: "渠道",
      dataIndex: "channel",
      width: 130,
      search: false,
      ellipsis: true,
    },
    {
      title: "标题",
      dataIndex: "title",
      width: 190,
      search: false,
      ellipsis: true,
      render: (_, record) => record.title || <Text type="secondary">-</Text>,
    },
    {
      title: "重试次数",
      dataIndex: "retryCount",
      width: 100,
      search: false,
      sorter: (left, right) => left.retryCount - right.retryCount,
    },
    {
      title: "失败原因",
      dataIndex: "lastError",
      width: 260,
      search: false,
      ellipsis: true,
      render: (_, record) => record.lastError || <Text type="secondary">-</Text>,
    },
    {
      title: "下次重试",
      dataIndex: "nextRetryAt",
      width: 170,
      search: false,
      render: (_, record) => formatTime(record.nextRetryAt),
    },
    {
      title: "接收时间",
      dataIndex: "acceptedAt",
      width: 170,
      search: false,
      render: (_, record) => formatTime(record.acceptedAt),
    },
  ];

  return (
    <div style={{ padding: 16 }}>
      <OperationsSummaryBar
        outbox={outboxSummary}
        delivery={deliverySummary}
        loading={summaryLoading}
        errors={summaryErrors}
        onRefresh={() => void loadSummaries()}
      />
      <ProTable<NotificationEventView>
        headerTitle="通知事件"
        actionRef={actionRef}
        rowKey="eventId"
        columns={columns}
        request={async (params) => {
          try {
            const { current, pageSize, eventType, ticketId, recipientUserId, status } = params;
            const query: NotificationEventQueryParams = {
              pageNo: current,
              pageSize,
              eventType: eventType as string | undefined,
              ticketId: ticketId as string | undefined,
              recipientUserId: recipientUserId as string | undefined,
              status: status as NotificationEventStatus | undefined,
            };
            const result = await fetchNotificationEvents(query);
            return {
              data: result.records,
              total: result.total,
              success: true,
            };
          } catch (error) {
            message.error((error as Error).message || "通知事件查询失败");
            return { data: [], total: 0, success: false };
          }
        }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        search={{ labelWidth: "auto", collapsed: false }}
        scroll={{ x: 1840 }}
        dateFormatter="string"
      />
    </div>
  );
};

export default NotificationEvents;
