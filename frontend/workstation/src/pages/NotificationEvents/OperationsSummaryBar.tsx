import React from "react";
import { ReloadOutlined } from "@ant-design/icons";
import { Alert, Button, Flex, Space, Spin, Tag, Tooltip, Typography } from "antd";
import dayjs from "dayjs";
import type {
  NotificationDeliverySummary,
  NotificationOutboxSummary,
} from "../../types/notification";
import {
  getDeliveryMetrics,
  getCutoverState,
  getOutboxMetrics,
  getSummaryState,
  type SummaryMetric,
} from "./summaryPresentation";

const { Text } = Typography;

interface OperationsSummaryBarProps {
  outbox?: NotificationOutboxSummary;
  delivery?: NotificationDeliverySummary;
  loading: boolean;
  errors: string[];
  onRefresh: () => void;
}

interface SummaryGroupProps {
  testId: string;
  title: string;
  enabled: boolean;
  metrics: SummaryMetric[];
  oldestDueAt?: string | null;
  runtimeTags?: Array<{
    label: string;
    color: "default" | "processing" | "success" | "error";
  }>;
}

const toneColors: Record<SummaryMetric["tone"], string | undefined> = {
  default: undefined,
  processing: "processing",
  warning: "warning",
  error: "error",
  success: "success",
};

const formatTime = (value?: string | null) =>
  value ? dayjs(value).format("YYYY-MM-DD HH:mm:ss") : "-";

const SummaryGroup: React.FC<SummaryGroupProps> = ({
  testId,
  title,
  enabled,
  metrics,
  oldestDueAt,
  runtimeTags = [],
}) => {
  const state = getSummaryState(enabled);

  return (
    <section aria-label={title} style={{ minWidth: 0 }}>
      <Flex align="center" gap={8} wrap="wrap" style={{ marginBottom: 8 }}>
        <Text strong>{title}</Text>
        <Tag color={state.color}>{state.label}</Tag>
        {runtimeTags.map((runtimeTag) => (
          <Tag key={runtimeTag.label} color={runtimeTag.color}>
            {runtimeTag.label}
          </Tag>
        ))}
        <Text type="secondary">最老积压 {formatTime(oldestDueAt)}</Text>
      </Flex>
      <Flex gap={6} wrap="wrap">
        {metrics.map((metric) => (
          <Tag key={metric.key} color={toneColors[metric.tone]} style={{ marginInlineEnd: 0 }}>
            {metric.label}{" "}
            <Text strong data-testid={`${testId}-${metric.key}`}>
              {metric.value}
            </Text>
          </Tag>
        ))}
      </Flex>
    </section>
  );
};

const OperationsSummaryBar: React.FC<OperationsSummaryBarProps> = ({
  outbox,
  delivery,
  loading,
  errors,
  onRefresh,
}) => {
  const cutoverState = getCutoverState(outbox, delivery);

  return (
    <div
    style={{
      marginBottom: 12,
      padding: "10px 12px",
      border: "1px solid #f0f0f0",
      borderRadius: 6,
      background: "#fff",
    }}
  >
    <Flex align="center" justify="space-between" gap={12} wrap="wrap" style={{ marginBottom: errors.length ? 8 : 10 }}>
      <Space size={8} wrap>
        <Text strong>通知运行状态</Text>
        {cutoverState ? (
          <>
            <Tag color={cutoverState.color} data-testid="cutover-state">
              {cutoverState.label}
            </Tag>
            <Text type="secondary">{cutoverState.description}</Text>
          </>
        ) : null}
        {loading ? <Spin size="small" /> : null}
      </Space>
      <Tooltip title="刷新运行状态">
        <Button
          aria-label="刷新运行状态"
          icon={<ReloadOutlined />}
          size="small"
          type="text"
          loading={loading}
          onClick={onRefresh}
        />
      </Tooltip>
    </Flex>

    {errors.length ? (
      <Alert
        showIcon
        type="warning"
        message={errors.join("；")}
        style={{ marginBottom: 10 }}
      />
    ) : null}

    <div
      style={{
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))",
        gap: "10px 20px",
      }}
    >
      {outbox ? (
        <SummaryGroup
          testId="outbox"
          title="Workbench Outbox"
          enabled={outbox.enabled}
          metrics={getOutboxMetrics(outbox)}
          oldestDueAt={outbox.oldestDueAt}
          runtimeTags={[
            {
              label: outbox.notificationEnabled ? "Notification 已开启" : "Notification 已关闭",
              color: outbox.notificationEnabled ? "success" : "error",
            },
            {
              label: outbox.userMessageDeliveryMode === "NOTIFICATION" ? "消息异步" : "消息直写",
              color: outbox.userMessageDeliveryMode === "NOTIFICATION" ? "processing" : "default",
            },
          ]}
        />
      ) : null}
      {delivery ? (
        <SummaryGroup
          testId="delivery"
          title="Notification Delivery"
          enabled={delivery.enabled}
          metrics={getDeliveryMetrics(delivery)}
          oldestDueAt={delivery.oldestDueAt}
          runtimeTags={[
            {
              label: delivery.userSessionChannelEnabled ? "USER_SESSION 已开启" : "USER_SESSION 已关闭",
              color: delivery.userSessionChannelEnabled ? "success" : "error",
            },
          ]}
        />
      ) : null}
    </div>
    </div>
  );
};

export default OperationsSummaryBar;
