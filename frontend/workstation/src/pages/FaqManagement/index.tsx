import React, { useRef, useState } from "react";
import {
  Button,
  Form,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Select,
  Space,
  Tag,
  Typography,
} from "antd";
import {
  EditOutlined,
  PlusOutlined,
  StopOutlined,
  CheckCircleOutlined,
  SyncOutlined,
} from "@ant-design/icons";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { ProTable } from "@ant-design/pro-components";
import dayjs from "dayjs";
import {
  createFaq,
  fetchFaqs,
  repairFaqIndexes,
  updateFaq,
  updateFaqStatus,
} from "../../services/knowledgeApi";
import type {
  FaqItem,
  FaqQueryParams,
  FaqStatus,
} from "../../types/knowledge";
import {
  toFaqFormValues,
  toFaqUpsertRequest,
  type FaqFormValues,
} from "./formUtils";

const { Text } = Typography;

const FAQ_STATUS_META: Record<FaqStatus, { text: string; color: string }> = {
  DRAFT: { text: "草稿", color: "default" },
  ACTIVE: { text: "启用", color: "success" },
  DISABLED: { text: "停用", color: "warning" },
};

const statusOptions: { label: string; value: FaqStatus }[] = [
  { label: "草稿", value: "DRAFT" },
  { label: "启用", value: "ACTIVE" },
  { label: "停用", value: "DISABLED" },
];

const FaqManagement: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const [form] = Form.useForm<FaqFormValues>();
  const [modalOpen, setModalOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<FaqItem | null>(null);
  const [saving, setSaving] = useState(false);
  const [repairing, setRepairing] = useState(false);

  const openCreate = () => {
    setEditingRecord(null);
    form.setFieldsValue(toFaqFormValues());
    setModalOpen(true);
  };

  const openEdit = (record: FaqItem) => {
    setEditingRecord(record);
    form.setFieldsValue(toFaqFormValues(record));
    setModalOpen(true);
  };

  const closeModal = () => {
    setModalOpen(false);
    setEditingRecord(null);
    form.resetFields();
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    const payload = toFaqUpsertRequest(values);
    if (payload.keywords.length === 0) {
      message.warning("请至少填写一个关键词");
      return;
    }

    setSaving(true);
    try {
      if (editingRecord) {
        await updateFaq(editingRecord.faqId, payload);
        message.success("FAQ 已更新");
      } else {
        await createFaq(payload);
        message.success("FAQ 已新增");
      }
      closeModal();
      actionRef.current?.reload();
    } catch (error) {
      message.error((error as Error).message || "保存 FAQ 失败");
    } finally {
      setSaving(false);
    }
  };

  const handleStatusChange = async (record: FaqItem, status: FaqStatus) => {
    try {
      await updateFaqStatus(record.faqId, status);
      message.success(status === "ACTIVE" ? "FAQ 已启用" : "FAQ 已停用");
      actionRef.current?.reload();
    } catch (error) {
      message.error((error as Error).message || "状态更新失败");
    }
  };

  const handleRepairIndexes = async () => {
    setRepairing(true);
    try {
      const result = await repairFaqIndexes();
      if (!result.enabled) {
        message.info("当前未启用混合检索索引，无需修复");
      } else if (result.failureCount > 0) {
        message.warning(
          `索引修复部分失败：成功 ${result.successCount} 项，失败 ${result.failureCount} 项`,
        );
      } else {
        message.success(`索引修复完成：${result.documentCount} 条 FAQ`);
      }
    } catch (error) {
      message.error((error as Error).message || "索引修复失败");
    } finally {
      setRepairing(false);
    }
  };

  const columns: ProColumns<FaqItem>[] = [
    {
      title: "FAQ ID",
      dataIndex: "faqId",
      width: 180,
      copyable: true,
      ellipsis: true,
      search: false,
    },
    {
      title: "问题",
      dataIndex: "question",
      ellipsis: true,
      width: 240,
      search: false,
    },
    {
      title: "答案",
      dataIndex: "answer",
      ellipsis: true,
      width: 320,
      search: false,
    },
    {
      title: "关键词",
      dataIndex: "keywords",
      width: 220,
      search: false,
      render: (_, record) => (
        <Space size={[4, 4]} wrap>
          {record.keywords.length > 0
            ? record.keywords.map(keyword => <Tag key={keyword}>{keyword}</Tag>)
            : <Text type="secondary">-</Text>}
        </Space>
      ),
    },
    {
      title: "分类",
      dataIndex: "category",
      width: 120,
      search: false,
    },
    {
      title: "状态",
      dataIndex: "status",
      width: 100,
      valueType: "select",
      fieldProps: { options: statusOptions },
      render: (_, record) => {
        const meta = FAQ_STATUS_META[record.status];
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: "优先级",
      dataIndex: "priority",
      width: 90,
      search: false,
      sorter: (a, b) => a.priority - b.priority,
    },
    {
      title: "关键词",
      dataIndex: "keyword",
      hideInTable: true,
      fieldProps: { placeholder: "搜索问题、答案或关键词" },
    },
    {
      title: "更新时间",
      dataIndex: "updatedAt",
      width: 170,
      search: false,
      render: (_, record) =>
        record.updatedAt ? dayjs(record.updatedAt).format("YYYY-MM-DD HH:mm:ss") : "-",
    },
    {
      title: "操作",
      valueType: "option",
      width: 180,
      fixed: "right",
      render: (_, record) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => openEdit(record)}
          >
            编辑
          </Button>
          {record.status === "ACTIVE" ? (
            <Popconfirm
              title="停用 FAQ"
              description="停用后不再参与用户 FAQ 查询。"
              okText="停用"
              cancelText="取消"
              onConfirm={() => handleStatusChange(record, "DISABLED")}
            >
              <Button type="link" size="small" icon={<StopOutlined />}>
                停用
              </Button>
            </Popconfirm>
          ) : (
            <Button
              type="link"
              size="small"
              icon={<CheckCircleOutlined />}
              onClick={() => handleStatusChange(record, "ACTIVE")}
            >
              启用
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 16 }}>
      <ProTable<FaqItem>
        headerTitle="FAQ 管理"
        actionRef={actionRef}
        rowKey="faqId"
        columns={columns}
        request={async (params) => {
          try {
            const { current, pageSize, status, keyword } = params;
            const query: FaqQueryParams = {
              pageNo: current,
              pageSize,
              status: status as FaqStatus | undefined,
              keyword: keyword as string | undefined,
            };
            const result = await fetchFaqs(query);
            return {
              data: result.records,
              total: result.total,
              success: true,
            };
          } catch (error) {
            message.error((error as Error).message || "FAQ 查询失败");
            return { data: [], total: 0, success: false };
          }
        }}
        toolbar={{
          actions: [
            <Popconfirm
              key="repair"
              title="修复知识索引"
              description="将 MySQL 中的 FAQ 重新同步到 ES 和 pgvector，不会清空现有索引；过程会调用向量模型。"
              okText="开始修复"
              cancelText="取消"
              onConfirm={handleRepairIndexes}
            >
              <Button icon={<SyncOutlined />} loading={repairing}>
                修复索引
              </Button>
            </Popconfirm>,
            <Button
              key="create"
              type="primary"
              icon={<PlusOutlined />}
              onClick={openCreate}
            >
              新增 FAQ
            </Button>,
          ],
        }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        search={{ labelWidth: "auto", collapsed: false }}
        scroll={{ x: 1320 }}
        dateFormatter="string"
      />

      <Modal
        title={editingRecord ? "编辑 FAQ" : "新增 FAQ"}
        open={modalOpen}
        confirmLoading={saving}
        width={720}
        okText="保存"
        cancelText="取消"
        onCancel={closeModal}
        onOk={handleSave}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={toFaqFormValues()}
          preserve={false}
        >
          <Form.Item
            label="问题"
            name="question"
            rules={[{ required: true, message: "请输入 FAQ 问题" }]}
          >
            <Input maxLength={200} showCount placeholder="例如：退款多久到账" />
          </Form.Item>
          <Form.Item
            label="答案"
            name="answer"
            rules={[{ required: true, message: "请输入 FAQ 答案" }]}
          >
            <Input.TextArea
              rows={5}
              maxLength={2000}
              showCount
              placeholder="输入用户可见的知识库回复"
            />
          </Form.Item>
          <Form.Item
            label="关键词"
            name="keywordsText"
            rules={[{ required: true, message: "请输入关键词" }]}
          >
            <Input.TextArea
              rows={2}
              maxLength={300}
              showCount
              placeholder="用逗号、分号或换行分隔"
            />
          </Form.Item>
          <Space size={16} style={{ width: "100%" }} align="start">
            <Form.Item
              label="分类"
              name="category"
              rules={[{ required: true, message: "请输入分类" }]}
              style={{ width: 220 }}
            >
              <Input maxLength={64} placeholder="after_sale" />
            </Form.Item>
            <Form.Item
              label="状态"
              name="status"
              rules={[{ required: true, message: "请选择状态" }]}
              style={{ width: 180 }}
            >
              <Select options={statusOptions} />
            </Form.Item>
            <Form.Item
              label="优先级"
              name="priority"
              rules={[{ required: true, message: "请输入优先级" }]}
              style={{ width: 160 }}
            >
              <InputNumber min={0} max={9999} style={{ width: "100%" }} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </div>
  );
};

export default FaqManagement;
