import type { FaqItem, FaqStatus, FaqUpsertRequest } from "../../types/knowledge";

export type FaqFormValues = {
  question: string;
  answer: string;
  keywordsText: string;
  category: string;
  status: FaqStatus;
  priority: number;
};

/** 将运营人员输入的多种分隔符统一转换为 Knowledge API 所需的关键词数组。 */
export function splitFaqKeywords(value: string): string[] {
  return value
    .split(/[,，;；\n]/)
    .map(item => item.trim())
    .filter(Boolean);
}

/** 将已有 FAQ 映射为编辑弹窗的初始值。 */
export function toFaqFormValues(record?: FaqItem): FaqFormValues {
  return {
    question: record?.question || "",
    answer: record?.answer || "",
    keywordsText: record?.keywords?.join("，") || "",
    category: record?.category || "general",
    status: record?.status || "ACTIVE",
    priority: record?.priority ?? 10,
  };
}

/** 只组装 Knowledge API 已定义的 FAQ 新增和更新字段。 */
export function toFaqUpsertRequest(values: FaqFormValues): FaqUpsertRequest {
  return {
    question: values.question.trim(),
    answer: values.answer.trim(),
    keywords: splitFaqKeywords(values.keywordsText),
    category: values.category.trim(),
    status: values.status,
    priority: values.priority,
  };
}
